package com.saldev.turnovivo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BusinessCreateRequest {

    @NotBlank(message = "The business name is required")
    @Size(min = 3, message = "The business name must to have at least 3 characters")
    private String name;

    @NotBlank(message = "The slug is required.")
    @Pattern(
            regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
            message = "El slug debe ser una cadena válida en minúsculas separada por guiones (ej: barberia-premium)."
    )
    private String slug;

    @NotNull(message = "El dueño de la empresa es obligatorio.")
    private UUID ownerId;
}
