package com.saldev.turnovivo.service;

import com.saldev.turnovivo.dto.response.AppointmentResponse;
import com.saldev.turnovivo.exception.BusinessException;
import com.saldev.turnovivo.model.entity.Appointment;
import com.saldev.turnovivo.model.entity.AppointmentSlot;
import com.saldev.turnovivo.model.entity.User;
import com.saldev.turnovivo.model.entity.WaitlistEntry;
import com.saldev.turnovivo.model.enums.AppointmentStatus;
import com.saldev.turnovivo.model.enums.BlockReason;
import com.saldev.turnovivo.model.enums.Role;
import com.saldev.turnovivo.model.enums.SlotStatus;
import com.saldev.turnovivo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTests {

    @Mock private AppointmentSlotRepository slotRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private WaitlistRepository waitlistRepository;
    @Mock private SlotBlockRepository slotBlockRepository;

    private AppointmentService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(
                slotRepository,
                appointmentRepository,
                userRepository,
                redisTemplate,
                waitlistRepository,
                slotBlockRepository,
                Clock.system(ZoneId.of("America/Mexico_City")),
                30
        );
    }

    @Test
    void bookingReservesTheSlotAndReturnsTheFrontendResponseShape() {
        UUID customerId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        User customer = User.builder().id(customerId).build();
        AppointmentSlot slot = slot(slotId, SlotStatus.AVAILABLE);

        when(userRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        when(slotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));
        when(slotBlockRepository.findBySlotIdAndCustomerId(slotId, customerId)).thenReturn(Optional.empty());
        when(appointmentRepository.existsActiveFutureAppointment(
                eq(customerId),
                eq(AppointmentStatus.SCHEDULED),
                any(ZonedDateTime.class)
        )).thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            appointment.setId(appointmentId);
            return appointment;
        });

        AppointmentResponse response = service.bookAppointment(slotId, customerId);

        assertThat(response).isEqualTo(new AppointmentResponse(appointmentId, customerId));
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.RESERVED);
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    void preventsASecondActiveAppointmentWhileOneIsStillUpcoming() {
        UUID customerId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        User customer = User.builder().id(customerId).build();
        AppointmentSlot slot = slot(slotId, SlotStatus.AVAILABLE);

        when(userRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        when(slotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));
        when(slotBlockRepository.findBySlotIdAndCustomerId(slotId, customerId)).thenReturn(Optional.empty());
        when(appointmentRepository.existsActiveFutureAppointment(
                eq(customerId),
                eq(AppointmentStatus.SCHEDULED),
                any(ZonedDateTime.class)
        )).thenReturn(true);

        assertThatThrownBy(() -> service.bookAppointment(slotId, customerId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(appointmentRepository, never()).save(any(Appointment.class));
        verify(slotRepository, never()).save(any(AppointmentSlot.class));
    }

    @Test
    void expiredWaitlistOfferIsRemovedAndTheNextCustomerReceivesThirtySeconds() {
        UUID slotId = UUID.randomUUID();
        UUID expiredCustomerId = UUID.randomUUID();
        UUID nextCustomerId = UUID.randomUUID();
        AppointmentSlot slot = slot(slotId, SlotStatus.HOLD);
        User expiredCustomer = User.builder().id(expiredCustomerId).build();
        User nextCustomer = User.builder().id(nextCustomerId).build();
        WaitlistEntry expiredEntry = WaitlistEntry.builder().slot(slot).customer(expiredCustomer).priority(1).build();
        WaitlistEntry nextEntry = WaitlistEntry.builder().slot(slot).customer(nextCustomer).priority(2).build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(slotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));
        when(redisTemplate.hasKey("hold:" + slotId)).thenReturn(false);
        when(waitlistRepository.findFirstBySlotIdOrderByPriorityAscCreatedAtAsc(slotId))
                .thenReturn(Optional.of(expiredEntry), Optional.of(nextEntry));
        when(slotBlockRepository.findBySlotIdAndCustomerId(slotId, expiredCustomerId))
                .thenReturn(Optional.empty());

        service.handleHoldExpiration(slotId.toString());

        verify(waitlistRepository).delete(expiredEntry);
        verify(slotBlockRepository).save(argThat(block -> block.getCustomer().getId().equals(expiredCustomerId)
                && block.getReason().name().equals("EXPIRED")));
        verify(valueOperations).set("hold:" + slotId, nextCustomerId.toString(), Duration.ofSeconds(30));
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.HOLD);
    }

    @Test
    void cancellingAnAppointmentOffersTheFreedSlotToTheFirstPersonInLine() {
        UUID slotId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID nextCustomerId = UUID.randomUUID();
        AppointmentSlot slot = slot(slotId, SlotStatus.RESERVED);
        User customer = User.builder().id(customerId).role(Role.CUSTOMER).build();
        User nextCustomer = User.builder().id(nextCustomerId).role(Role.CUSTOMER).build();
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .slot(slot)
                .customer(customer)
                .status(AppointmentStatus.SCHEDULED)
                .build();
        WaitlistEntry nextEntry = WaitlistEntry.builder()
                .slot(slot)
                .customer(nextCustomer)
                .priority(1)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(userRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(slotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));
        when(slotBlockRepository.findBySlotIdAndCustomerId(slotId, customerId)).thenReturn(Optional.empty());
        when(waitlistRepository.findFirstBySlotIdOrderByPriorityAscCreatedAtAsc(slotId))
                .thenReturn(Optional.of(nextEntry));

        String message = service.cancelAppointment(appointmentId, customerId);

        assertThat(message).contains("bloqueado en HOLD");
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.HOLD);
        verify(appointmentRepository).delete(appointment);
        verify(slotBlockRepository).save(argThat(block -> block.getReason() == BlockReason.CANCELED));
        verify(valueOperations).set("hold:" + slotId, nextCustomerId.toString(), Duration.ofSeconds(30));
    }

    @Test
    void adminCannotCancelAppointmentFromAnotherOwnersBusiness() {
        UUID slotId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID requestingAdminId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User customer = User.builder().id(customerId).role(Role.CUSTOMER).build();
        User requestingAdmin = User.builder().id(requestingAdminId).role(Role.ADMIN).build();
        AppointmentSlot slot = slot(slotId, SlotStatus.RESERVED);
        slot.getBusiness().setOwner(User.builder().id(ownerId).role(Role.ADMIN).build());
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .slot(slot)
                .customer(customer)
                .status(AppointmentStatus.SCHEDULED)
                .build();
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(userRepository.findByIdForUpdate(requestingAdminId)).thenReturn(Optional.of(requestingAdmin));
        when(userRepository.findById(requestingAdminId)).thenReturn(Optional.of(requestingAdmin));
        when(slotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.cancelAppointment(appointmentId, requestingAdminId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(appointmentRepository, never()).delete(any(Appointment.class));
    }

    private AppointmentSlot slot(UUID id, SlotStatus status) {
        return AppointmentSlot.builder()
                .id(id)
                .business(com.saldev.turnovivo.model.entity.Business.builder().id(UUID.randomUUID()).build())
                .staff(User.builder().id(UUID.randomUUID()).build())
                .startTime(ZonedDateTime.now(ZoneId.of("America/Mexico_City")).plusDays(1))
                .endTime(ZonedDateTime.now(ZoneId.of("America/Mexico_City")).plusDays(1).plusMinutes(30))
                .status(status)
                .build();
    }
}
