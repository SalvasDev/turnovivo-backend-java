package com.saldev.turnovivo.dto.response;

import com.saldev.turnovivo.model.enums.Role;

import java.util.UUID;

public record UserResponse(UUID id, String email, Role role) {
}
