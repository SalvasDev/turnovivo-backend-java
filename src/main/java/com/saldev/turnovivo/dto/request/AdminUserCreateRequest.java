package com.saldev.turnovivo.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserCreateRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "The email format is invalid.")
        String email,

        @NotBlank(message = "The password is required.")
        @Size(min = 6, message = "The password must be at least 6 characters long.")
        String password
) {
}
