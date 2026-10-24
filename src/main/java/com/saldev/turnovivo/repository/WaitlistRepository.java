package com.saldev.turnovivo.repository;

import com.saldev.turnovivo.model.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, UUID> {
    /*
     * Finds the first user on the waitlist for a specific slot.
     * Sorts first by ascending priority (1 is the highest), then by creation
     * time to resolve priority ties according to arrival order.
     */
    Optional<WaitlistEntry> findFirstBySlotIdOrderByPriorityAscCreatedAtAsc(UUID slotId);

    List<WaitlistEntry> findAllBySlotIdInOrderByPriorityAscCreatedAtAsc(Collection<UUID> slotIds);

    Optional<WaitlistEntry> findBySlotIdAndCustomerId(UUID slotId, UUID customerId);

    boolean existsBySlotIdAndCustomerId(UUID slotId, UUID customerId);

    void deleteBySlotIdAndCustomerId(UUID slotId, UUID customerId);
}
