package com.saldev.turnovivo.repository;

import com.saldev.turnovivo.model.entity.AppointmentSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

@Repository
public interface AppointmentSlotRepository extends JpaRepository<AppointmentSlot, UUID> {
    /*
    * Synchronized with AppointmentService: "findAvailableSlots(businessId)"
    *
    * Enterprise pattern: JPQL (Java Persistence Query Language).
    * Instead of loading thousands of records into memory and filtering them in the application,
    * an indexed JOIN runs directly in PostgreSQL and returns only the business slots whose
    * start time is equal to or later than the current time.
    */

    boolean existsByBusinessIdAndStaffIdAndStartTime(
            UUID businessId,
            UUID staffId,
            ZonedDateTime startTime
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AppointmentSlot s WHERE s.id = :slotId")
    Optional<AppointmentSlot> findByIdForUpdate(@Param("slotId") UUID slotId);

    @Query("""
            SELECT s
            FROM AppointmentSlot s
            WHERE s.business.id = :businessId
              AND s.business.active = true
              AND s.startTime >= :now
            ORDER BY s.startTime ASC
            """)
    List<AppointmentSlot> findFutureSlotsByBusiness(
            @Param("businessId") UUID businessId,
            @Param("now") ZonedDateTime now
    );

    @Query("""
            SELECT s.startTime
            FROM AppointmentSlot s
            WHERE s.business.id = :businessId
              AND s.staff.id = :staffId
              AND s.startTime BETWEEN :windowStart AND :windowEnd
            """)
    List<ZonedDateTime> findStartTimes(
            @Param("businessId") UUID businessId,
            @Param("staffId") UUID staffId,
            @Param("windowStart") ZonedDateTime windowStart,
            @Param("windowEnd") ZonedDateTime windowEnd
    );
}
