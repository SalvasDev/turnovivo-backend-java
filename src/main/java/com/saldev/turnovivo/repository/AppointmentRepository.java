package com.saldev.turnovivo.repository;

import com.saldev.turnovivo.model.entity.Appointment;
import com.saldev.turnovivo.model.enums.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    /*
    * Synchronized with AppointmentService: "cancelAppointment"
    *
    * Spring Data JPA analyzes the method name (query method) and automatically generates:
    * SELECT * FROM appointments WHERE slot_id = ?
    */
    Optional<Appointment> findBySlotId(UUID slotId);

    Optional<Appointment> findBySlotIdAndStatus(UUID slotId, AppointmentStatus status);

    List<Appointment> findAllBySlotIdInAndStatus(Collection<UUID> slotIds, AppointmentStatus status);

    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM Appointment a
            WHERE a.customer.id = :customerId
              AND a.status = :status
              AND a.slot.startTime > :now
            """)
    boolean existsActiveFutureAppointment(
            @Param("customerId") UUID customerId,
            @Param("status") AppointmentStatus status,
            @Param("now") ZonedDateTime now
    );
}
