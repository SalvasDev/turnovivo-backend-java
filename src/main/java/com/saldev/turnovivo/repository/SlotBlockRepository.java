package com.saldev.turnovivo.repository;

import com.saldev.turnovivo.model.entity.SlotBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlotBlockRepository extends JpaRepository<SlotBlock, UUID> {
    List<SlotBlock> findAllBySlotIdIn(Collection<UUID> slotIds);

    Optional<SlotBlock> findBySlotIdAndCustomerId(UUID slotId, UUID customerId);
}
