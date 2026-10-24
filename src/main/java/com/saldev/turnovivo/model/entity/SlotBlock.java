package com.saldev.turnovivo.model.entity;

import com.saldev.turnovivo.model.enums.BlockReason;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "slot_blocks",
        // Prevents duplicate historical penalties for the same customer-slot pair.
        uniqueConstraints = @UniqueConstraint(columnNames = {"slot_id", "customer_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlotBlock {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private AppointmentSlot slot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlockReason reason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private ZonedDateTime createdAt;
}
