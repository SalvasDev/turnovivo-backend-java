package com.saldev.turnovivo.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "waitlist_entries",
        // Native composite unique constraint at the database level.
        // Prevents the same customer from joining the same slot's waitlist twice.
        uniqueConstraints = @UniqueConstraint(columnNames = {"slot_id", "customer_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistEntry {

    @Id@GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private AppointmentSlot slot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(nullable = false)
    private Integer priority;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private ZonedDateTime createdAt;
}
