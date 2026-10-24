package com.saldev.turnovivo.model.entity;

import com.saldev.turnovivo.model.enums.AppointmentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    // Strict one-to-one relationship with the slot. A reserved slot has only one appointment.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", unique = true, nullable = false)
    private AppointmentSlot slot;

    // Many-to-one relationship with the customer who made the booking.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private ZonedDateTime createdAt;
}
