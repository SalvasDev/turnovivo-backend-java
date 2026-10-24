package com.saldev.turnovivo.service;

import com.saldev.turnovivo.dto.response.AppointmentResponse;
import com.saldev.turnovivo.dto.response.SlotResponse;
import com.saldev.turnovivo.dto.response.WaitlistResponse;
import com.saldev.turnovivo.exception.BusinessException;
import com.saldev.turnovivo.exception.ResourceNotFoundException;
import com.saldev.turnovivo.model.entity.*;
import com.saldev.turnovivo.model.enums.AppointmentStatus;
import com.saldev.turnovivo.model.enums.BlockReason;
import com.saldev.turnovivo.model.enums.Role;
import com.saldev.turnovivo.model.enums.SlotStatus;
import com.saldev.turnovivo.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private static final String HOLD_PREFIX = "hold:";

    private final AppointmentSlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final WaitlistRepository waitlistRepository;
    private final SlotBlockRepository slotBlockRepository;
    private final Clock clock;
    private final long holdTtlSeconds;

    public AppointmentService(AppointmentSlotRepository slotRepository,
                              AppointmentRepository appointmentRepository,
                              UserRepository userRepository,
                              RedisTemplate<String, String> redisTemplate,
                              WaitlistRepository waitlistRepository,
                              SlotBlockRepository slotBlockRepository,
                              Clock clock,
                              @Value("${app.hold.ttl-seconds:30}") long holdTtlSeconds) {
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.waitlistRepository = waitlistRepository;
        this.slotBlockRepository = slotBlockRepository;
        this.clock = clock;
        this.holdTtlSeconds = holdTtlSeconds;
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> findAvailableSlots(UUID businessId) {
        List<AppointmentSlot> slots = slotRepository.findFutureSlotsByBusiness(
                businessId,
                ZonedDateTime.now(clock)
        );
        if (slots.isEmpty()) {
            return List.of();
        }

        List<UUID> slotIds = slots.stream().map(AppointmentSlot::getId).toList();
        Map<UUID, Appointment> appointments = appointmentRepository
                .findAllBySlotIdInAndStatus(slotIds, AppointmentStatus.SCHEDULED)
                .stream()
                .collect(Collectors.toMap(appointment -> appointment.getSlot().getId(), Function.identity()));
        Map<UUID, List<WaitlistEntry>> waitlistEntries = waitlistRepository
                .findAllBySlotIdInOrderByPriorityAscCreatedAtAsc(slotIds)
                .stream()
                .collect(Collectors.groupingBy(entry -> entry.getSlot().getId()));
        Map<UUID, List<SlotBlock>> slotBlocks = slotBlockRepository.findAllBySlotIdIn(slotIds)
                .stream()
                .collect(Collectors.groupingBy(block -> block.getSlot().getId()));

        return slots.stream()
                .map(slot -> {
                    UUID slotId = slot.getId();
                    Appointment appointment = appointments.get(slotId);
                    String holdOwner = slot.getStatus() == SlotStatus.HOLD
                            ? redisTemplate.opsForValue().get(holdKey(slotId))
                            : null;
                    return new SlotResponse(
                            slotId,
                            slot.getBusiness().getId(),
                            slot.getStaff().getId(),
                            slot.getStartTime(),
                            slot.getEndTime(),
                            slot.getStatus(),
                            appointment == null && holdOwner == null
                                    ? null
                                    : new SlotResponse.AppointmentSummary(
                                            appointment == null ? null : appointment.getId(),
                                            appointment == null ? UUID.fromString(holdOwner) : appointment.getCustomer().getId()
                                    ),
                            waitlistEntries.getOrDefault(slotId, List.of()).stream()
                                    .map(entry -> new SlotResponse.WaitlistSummary(entry.getCustomer().getId()))
                                    .toList(),
                            slotBlocks.getOrDefault(slotId, List.of()).stream()
                                    .map(block -> new SlotResponse.SlotBlockSummary(
                                            block.getCustomer().getId(),
                                            block.getReason() == BlockReason.CANCELED ? "CANCELLED" : block.getReason().name()
                                    ))
                                    .toList()
                    );
                })
                .toList();
    }

    @Transactional
    public AppointmentResponse bookAppointment(UUID slotId, UUID customerId) {
        User customer = userRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer record not found."));
        ensureCustomer(customer);
        AppointmentSlot slot = getLockedSlot(slotId);
        ensureActiveBusiness(slot);

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new BusinessException("This appointment slot is no longer available.", HttpStatus.CONFLICT);
        }
        ensureNotBlocked(slotId, customerId);
        ensureNoActiveAppointment(customerId);

        slot.setStatus(SlotStatus.RESERVED);
        slotRepository.save(slot);
        Appointment appointment = appointmentRepository.save(Appointment.builder()
                .slot(slot)
                .customer(customer)
                .status(AppointmentStatus.SCHEDULED)
                .build());
        return new AppointmentResponse(appointment.getId(), customerId);
    }

    @Transactional
    public WaitlistResponse joinWaitlist(UUID slotId, UUID customerId) {
        AppointmentSlot slot = getLockedSlot(slotId);
        ensureActiveBusiness(slot);
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer record not found."));
        ensureCustomer(customer);
        if (slot.getStatus() == SlotStatus.AVAILABLE) {
            throw new BusinessException(
                    "This slot is available. You can book it directly, without having to wait in line.",
                    HttpStatus.CONFLICT
            );
        }
        ensureNotBlocked(slotId, customerId);
        if (waitlistRepository.existsBySlotIdAndCustomerId(slotId, customerId)) {
            throw new BusinessException(
                    "You are already registered on the waiting list for this time slot.",
                    HttpStatus.CONFLICT
            );
        }

        List<WaitlistEntry> entries = waitlistRepository
                .findAllBySlotIdInOrderByPriorityAscCreatedAtAsc(List.of(slotId));
        int nextPriority = entries.stream()
                .mapToInt(WaitlistEntry::getPriority)
                .max()
                .orElse(0) + 1;

        WaitlistEntry entry = waitlistRepository.save(WaitlistEntry.builder()
                .slot(slot)
                .customer(customer)
                .priority(nextPriority)
                .build());
        return new WaitlistResponse(entry.getId(), customerId);
    }

    @Transactional
    public String cancelAppointment(UUID appointmentId, UUID requestingUserId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("The specified appointment does not exist."));
        userRepository.findByIdForUpdate(requestingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User record not found."));
        UUID slotId = appointment.getSlot().getId();
        AppointmentSlot slot = getLockedSlot(slotId);
        appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("The specified appointment does not exist."));

        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new BusinessException("This appointment has already been cancelled.", HttpStatus.CONFLICT);
        }
        User requestingUser = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User record not found."));
        boolean isStaff = requestingUser.getRole() == Role.STAFF;
        boolean isOwnerAdmin = requestingUser.getRole() == Role.ADMIN
                && appointment.getSlot().getBusiness().getOwner().getId().equals(requestingUserId);
        if (!appointment.getCustomer().getId().equals(requestingUserId) && !isStaff && !isOwnerAdmin) {
            throw new BusinessException("You do not have authorization to cancel this appointment.", HttpStatus.FORBIDDEN);
        }

        saveBlock(slot, appointment.getCustomer(), BlockReason.CANCELED);
        appointmentRepository.delete(appointment);
        if (promoteNextInWaitlist(slot)) {
            return "Appointment successfully cancelled. The slot has been placed on HOLD.";
        }
        return "Appointment successfully cancelled. The time slot is once again available to the public.";
    }

    @Transactional
    public AppointmentResponse confirmHold(UUID slotId, UUID customerId) {
        User customer = userRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer record not found."));
        ensureCustomer(customer);
        AppointmentSlot slot = getLockedSlot(slotId);
        ensureActiveBusiness(slot);
        String redisKey = holdKey(slotId);
        ensureHoldOwner(redisKey, customerId);
        ensureNoActiveAppointment(customerId);

        slot.setStatus(SlotStatus.RESERVED);
        slotRepository.save(slot);
        waitlistRepository.deleteBySlotIdAndCustomerId(slotId, customerId);
        Appointment appointment = appointmentRepository.save(Appointment.builder()
                .slot(slot)
                .customer(customer)
                .status(AppointmentStatus.SCHEDULED)
                .build());
        redisTemplate.delete(redisKey);
        return new AppointmentResponse(appointment.getId(), customerId);
    }

    @Transactional
    public String declineHold(UUID slotId, UUID customerId) {
        AppointmentSlot slot = getLockedSlot(slotId);
        String redisKey = holdKey(slotId);
        ensureHoldOwner(redisKey, customerId);

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer record not found."));
        ensureCustomer(customer);
        waitlistRepository.deleteBySlotIdAndCustomerId(slotId, customerId);
        saveBlock(slot, customer, BlockReason.DECLINED);
        redisTemplate.delete(redisKey);
        promoteNextInWaitlist(slot);
        return "You have declined the turn. It will be offered to the next person in line.";
    }

    @Transactional
    public String leaveWaitlist(UUID slotId, UUID customerId) {
        AppointmentSlot slot = getLockedSlot(slotId);
        String redisKey = holdKey(slotId);
        boolean isCurrentOffer = customerId.toString().equals(redisTemplate.opsForValue().get(redisKey));
        WaitlistEntry entry = waitlistRepository.findBySlotIdAndCustomerId(slotId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "You are not registered on the waiting list for this time slot."
                ));
        waitlistRepository.delete(entry);
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer record not found."));
        ensureCustomer(customer);
        saveBlock(slot, customer, BlockReason.DECLINED);

        if (isCurrentOffer) {
            redisTemplate.delete(redisKey);
            promoteNextInWaitlist(slot);
        }
        return "You have successfully left the waiting line.";
    }

    @Transactional
    public void handleHoldExpiration(String slotIdStr) {
        UUID slotId = UUID.fromString(slotIdStr);
        AppointmentSlot slot = slotRepository.findByIdForUpdate(slotId).orElse(null);
        if (slot == null || slot.getStatus() != SlotStatus.HOLD) {
            return;
        }

        String redisKey = holdKey(slotId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            return;
        }

        WaitlistEntry expiredEntry = waitlistRepository
                .findFirstBySlotIdOrderByPriorityAscCreatedAtAsc(slotId)
                .orElse(null);
        if (expiredEntry != null) {
            waitlistRepository.delete(expiredEntry);
            saveBlock(slot, expiredEntry.getCustomer(), BlockReason.EXPIRED);
        }
        promoteNextInWaitlist(slot);
    }

    private AppointmentSlot getLockedSlot(UUID slotId) {
        return slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("The requested time block does not exist."));
    }

    private void ensureNoActiveAppointment(UUID customerId) {
        if (appointmentRepository.existsActiveFutureAppointment(
                customerId,
                AppointmentStatus.SCHEDULED,
                ZonedDateTime.now(clock)
        )) {
            throw new BusinessException(
                    "You can only have one active reservation at a time. " +
                            "Cancel your current appointment or wait for it " +
                            "to pass before booking another one.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void ensureCustomer(User user) {
        if (user.getRole() != Role.CUSTOMER) {
            throw new BusinessException(
                    "This operation is only available to clients.",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private void ensureActiveBusiness(AppointmentSlot slot) {
        if (!slot.getBusiness().isActive()) {
            throw new BusinessException(
                    "The company is not active.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void ensureNotBlocked(UUID slotId, UUID customerId) {
        if (slotBlockRepository.findBySlotIdAndCustomerId(slotId, customerId).isPresent()) {
            throw new BusinessException("You can no longer interact with this turn.", HttpStatus.FORBIDDEN);
        }
    }

    private void ensureHoldOwner(String redisKey, UUID customerId) {
        String activeUserInHold = redisTemplate.opsForValue().get(redisKey);
        if (activeUserInHold == null) {
            throw new BusinessException(
                    "The hold time has expired, or the slot is not available.",
                    HttpStatus.CONFLICT
            );
        }
        if (!activeUserInHold.equals(customerId.toString())) {
            throw new BusinessException(
                    "You do not have authorization to interact with this reserved appointment.",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private void saveBlock(AppointmentSlot slot, User customer, BlockReason reason) {
        SlotBlock block = slotBlockRepository.findBySlotIdAndCustomerId(slot.getId(), customer.getId())
                .orElseGet(() -> SlotBlock.builder().slot(slot).customer(customer).build());
        block.setReason(reason);
        slotBlockRepository.save(block);
    }

    private boolean promoteNextInWaitlist(AppointmentSlot slot) {
        WaitlistEntry nextEntry = waitlistRepository
                .findFirstBySlotIdOrderByPriorityAscCreatedAtAsc(slot.getId())
                .orElse(null);
        if (nextEntry == null) {
            slot.setStatus(SlotStatus.AVAILABLE);
            slotRepository.save(slot);
            return false;
        }

        slot.setStatus(SlotStatus.HOLD);
        slotRepository.save(slot);
        redisTemplate.opsForValue().set(
                holdKey(slot.getId()),
                nextEntry.getCustomer().getId().toString(),
                Duration.ofSeconds(holdTtlSeconds)
        );
        return true;
    }

    private String holdKey(UUID slotId) {
        return HOLD_PREFIX + slotId;
    }
}
