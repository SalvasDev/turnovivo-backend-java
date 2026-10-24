package com.saldev.turnovivo.mapper;

import com.saldev.turnovivo.dto.response.SlotResponse;
import com.saldev.turnovivo.model.entity.AppointmentSlot;
import org.springframework.stereotype.Component;

@Component
public class AppointmentMapper {

    public SlotResponse toResponse(AppointmentSlot slot) {
        return new SlotResponse(
                slot.getId(),
                slot.getBusiness().getId(),
                slot.getStaff().getId(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getStatus(),
                null,
                java.util.List.of(),
                java.util.List.of()
        );
    }
}
