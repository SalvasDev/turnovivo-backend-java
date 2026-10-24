package com.saldev.turnovivo.service;

import com.saldev.turnovivo.model.entity.AppointmentSlot;
import com.saldev.turnovivo.model.entity.Business;
import com.saldev.turnovivo.model.entity.User;
import com.saldev.turnovivo.model.enums.Role;
import com.saldev.turnovivo.repository.AppointmentSlotRepository;
import com.saldev.turnovivo.repository.BusinessRepository;
import com.saldev.turnovivo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotCalendarServiceTests {

    @Mock
    private AppointmentSlotRepository slotRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    void generatesFourteenCompleteDaysWithoutDuplicatingExistingSlots() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-15T16:00:00Z"),
                ZoneId.of("America/Mexico_City")
        );
        Business business = Business.builder().id(UUID.randomUUID()).build();
        User staff = User.builder().id(UUID.randomUUID()).role(Role.STAFF).build();
        ZonedDateTime existingStart = ZonedDateTime.of(
                2026, 10, 16, 9, 0, 0, 0, clock.getZone()
        );

        when(userRepository.findFirstByRole(Role.STAFF)).thenReturn(Optional.of(staff));
        when(slotRepository.findStartTimes(
                any(UUID.class),
                any(UUID.class),
                any(ZonedDateTime.class),
                any(ZonedDateTime.class)
        )).thenReturn(List.of(existingStart));

        SlotCalendarService service = new SlotCalendarService(
                slotRepository,
                businessRepository,
                userRepository,
                clock
        );

        service.reconcileBusiness(business);

        ArgumentCaptor<AppointmentSlot> captor = ArgumentCaptor.forClass(AppointmentSlot.class);
        verify(slotRepository, org.mockito.Mockito.times(223)).save(captor.capture());

        List<AppointmentSlot> generatedSlots = captor.getAllValues();
        assertThat(generatedSlots)
                .extracting(AppointmentSlot::getStartTime)
                .doesNotContain(existingStart)
                .contains(
                        ZonedDateTime.of(2026, 10, 16, 9, 30, 0, 0, clock.getZone()),
                        ZonedDateTime.of(2026, 10, 29, 16, 30, 0, 0, clock.getZone())
                );
        assertThat(generatedSlots)
                .allSatisfy(slot -> assertThat(slot.getEndTime())
                        .isEqualTo(slot.getStartTime().plusMinutes(30)));
    }

    @Test
    void doesNotInsertSlotsWhenTheWholeWindowAlreadyExists() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-15T16:00:00Z"),
                ZoneId.of("America/Mexico_City")
        );
        Business business = Business.builder().id(UUID.randomUUID()).build();
        User staff = User.builder().id(UUID.randomUUID()).role(Role.STAFF).build();
        ZonedDateTime firstStart = ZonedDateTime.of(
                2026, 10, 16, 9, 0, 0, 0, clock.getZone()
        );
        List<ZonedDateTime> existingStarts = java.util.stream.IntStream
                .range(0, SlotCalendarService.WINDOW_DAYS * SlotCalendarService.SLOTS_PER_DAY)
                .mapToObj(index -> firstStart
                        .plusDays(index / SlotCalendarService.SLOTS_PER_DAY)
                        .plusMinutes(30L * (index % SlotCalendarService.SLOTS_PER_DAY)))
                .toList();

        when(userRepository.findFirstByRole(Role.STAFF)).thenReturn(Optional.of(staff));
        when(slotRepository.findStartTimes(
                any(UUID.class),
                any(UUID.class),
                any(ZonedDateTime.class),
                any(ZonedDateTime.class)
        )).thenReturn(existingStarts);

        SlotCalendarService service = new SlotCalendarService(
                slotRepository,
                businessRepository,
                userRepository,
                clock
        );

        service.reconcileBusiness(business);

        verify(slotRepository, never()).save(any(AppointmentSlot.class));
    }

    @Test
    void treatsOffsetBasedTimestampsFromDatabaseAsExistingSlots() {
        // Regression: PostgreSQL returns timestamps with a numeric offset
        // (for example, "-06:00") instead of the original named zone used on insert
        // (for example, "America/Mexico_City"). ZonedDateTime.equals() would treat
        // them as different even when they represent the same instant, causing
        // repeated inserts and unique-constraint violations.
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-15T16:00:00Z"),
                ZoneId.of("America/Mexico_City")
        );
        Business business = Business.builder().id(UUID.randomUUID()).build();
        User staff = User.builder().id(UUID.randomUUID()).role(Role.STAFF).build();

        ZonedDateTime firstStartNamedZone = ZonedDateTime.of(
                2026, 10, 16, 9, 0, 0, 0, clock.getZone()
        );
        List<ZonedDateTime> existingStartsWithOffsetZone = java.util.stream.IntStream
                .range(0, SlotCalendarService.WINDOW_DAYS * SlotCalendarService.SLOTS_PER_DAY)
                .mapToObj(index -> firstStartNamedZone
                        .plusDays(index / SlotCalendarService.SLOTS_PER_DAY)
                        .plusMinutes(30L * (index % SlotCalendarService.SLOTS_PER_DAY)))
                // Simulate the JDBC driver result: the same instant represented
                // with a ZoneOffset instead of the named zone.
                .map(zonedDateTime -> zonedDateTime.withZoneSameInstant(ZoneOffset.of("-06:00")))
                .toList();

        when(userRepository.findFirstByRole(Role.STAFF)).thenReturn(Optional.of(staff));
        when(slotRepository.findStartTimes(
                any(UUID.class),
                any(UUID.class),
                any(ZonedDateTime.class),
                any(ZonedDateTime.class)
        )).thenReturn(existingStartsWithOffsetZone);

        SlotCalendarService service = new SlotCalendarService(
                slotRepository,
                businessRepository,
                userRepository,
                clock
        );

        service.reconcileBusiness(business);

        verify(slotRepository, never()).save(any(AppointmentSlot.class));
    }
}
