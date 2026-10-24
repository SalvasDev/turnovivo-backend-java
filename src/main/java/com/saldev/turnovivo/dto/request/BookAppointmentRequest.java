package com.saldev.turnovivo.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BookAppointmentRequest(@NotNull UUID slotId) {
}
