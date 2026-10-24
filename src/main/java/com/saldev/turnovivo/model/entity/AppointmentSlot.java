package com.saldev.turnovivo.model.entity;

import com.saldev.turnovivo.model.enums.SlotStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "appointment_slots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_slot_business_staff_start",
                columnNames = {"business_id", "staff_id", "start_time"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    // Unidirectional relationship: the slot knows which business it belongs to.
    // Use FetchType.LAZY to avoid loading unnecessary data when querying slots.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    // Unidirectional relationship: the slot knows which staff member handles it.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private User staff;

    @Column(name = "start_time", nullable = false)
    private ZonedDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private ZonedDateTime endTime;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SlotStatus status = SlotStatus.AVAILABLE;
}
