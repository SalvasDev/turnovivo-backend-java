package com.saldev.turnovivo.service;

import com.saldev.turnovivo.exception.BusinessException;
import com.saldev.turnovivo.model.entity.AppointmentSlot;
import com.saldev.turnovivo.model.entity.Business;
import com.saldev.turnovivo.model.entity.User;
import com.saldev.turnovivo.model.enums.Role;
import com.saldev.turnovivo.model.enums.SlotStatus;
import com.saldev.turnovivo.repository.AppointmentSlotRepository;
import com.saldev.turnovivo.repository.BusinessRepository;
import com.saldev.turnovivo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SlotCalendarService {

    static final int WINDOW_DAYS = 14;
    static final int SLOTS_PER_DAY = 16;
    private static final LocalTime OPENING_TIME = LocalTime.of(10, 0);

    private final AppointmentSlotRepository slotRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public SlotCalendarService(
            AppointmentSlotRepository slotRepository,
            BusinessRepository businessRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.slotRepository = slotRepository;
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void reconcileAtMidnight() {
        reconcileAllBusinesses();
    }

    @Transactional
    public void reconcileAllBusinesses() {
        User staff = findDefaultStaff();
        businessRepository.findAllByActiveTrue().forEach(business -> reconcileBusiness(business, staff));
    }

    @Transactional
    public void reconcileBusiness(Business business) {
        if (!business.isActive()) {
            throw new BusinessException("An agenda cannot be generated for an inactive company.", HttpStatus.CONFLICT);
        }
        reconcileBusiness(business, findDefaultStaff());
    }

    private void reconcileBusiness(Business business, User staff) {
        LocalDate firstDay = LocalDate.now(clock).plusDays(1);
        ZonedDateTime windowStart = firstDay.atTime(OPENING_TIME).atZone(clock.getZone());
        ZonedDateTime windowEnd = firstDay.plusDays(WINDOW_DAYS - 1)
                .atTime(OPENING_TIME)
                .plusMinutes(30L * (SLOTS_PER_DAY - 1))
                .atZone(clock.getZone());

        /* Compare absolute instants rather than complete ZonedDateTime values:
        * PostgreSQL returns timestamps with a numeric offset (for example, "-06:00")
        * instead of the original named zone (for example, "America/Mexico_City"), and
        * ZonedDateTime.equals() treats them as different even when they represent the
        * same instant. Using Instant prevents false negatives that would cause duplicate
        * inserts and violate the table's unique constraint.
        */
        Set<Instant> existingStartInstants = slotRepository.findStartTimes(
                        business.getId(),
                        staff.getId(),
                        windowStart,
                        windowEnd
                ).stream()
                .map(ZonedDateTime::toInstant)
                .collect(Collectors.toCollection(HashSet::new));

        for (int day = 0; day < WINDOW_DAYS; day++) {
            ZonedDateTime slotStart = firstDay.plusDays(day)
                    .atTime(OPENING_TIME)
                    .atZone(clock.getZone());

            for (int slotNumber = 0; slotNumber < SLOTS_PER_DAY; slotNumber++) {
                if (!existingStartInstants.contains(slotStart.toInstant())) {
                    slotRepository.save(AppointmentSlot.builder()
                            .business(business)
                            .staff(staff)
                            .startTime(slotStart)
                            .endTime(slotStart.plusMinutes(30))
                            .status(SlotStatus.AVAILABLE)
                            .build());
                }

                slotStart = slotStart.plusMinutes(30);
            }
        }
    }

    private User findDefaultStaff() {
        return userRepository.findFirstByRole(Role.STAFF)
                .orElseThrow(() -> new BusinessException(
                        "No STAFF user is available to generate appointment slots.",
                        HttpStatus.SERVICE_UNAVAILABLE
                ));
    }
}
