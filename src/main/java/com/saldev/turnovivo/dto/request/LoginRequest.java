package com.saldev.turnovivo.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {

    @NotBlank(message = "The email is required")
    @Email(message = "The email format is invalid.")
    private String email;

    @NotBlank(message = "The password is required")
    @Size(min = 6, message = "The password must to have 6 characters at least")
    private String passwordHash;
}
