package com.saldev.turnovivo.dto.response;

import com.saldev.turnovivo.model.enums.SlotStatus;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record SlotResponse(
        UUID id,
        UUID businessId,
        UUID staffId,
        ZonedDateTime startTime,
        ZonedDateTime endTime,
        SlotStatus status,
        AppointmentSummary appointment,
        List<WaitlistSummary> waitlistEntries,
        List<SlotBlockSummary> slotBlocks
) {
    public record AppointmentSummary(UUID id, UUID customerId) {
    }

    public record WaitlistSummary(UUID customerId) {
    }

    public record SlotBlockSummary(UUID customerId, String reason) {
    }
}
