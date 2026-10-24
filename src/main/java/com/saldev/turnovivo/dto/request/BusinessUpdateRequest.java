package com.saldev.turnovivo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
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
public class BusinessUpdateRequest {

    @NotBlank(message = "The business name is required.")
    @Size(min = 3, message = "The business name mus to have at least 3 chareacters")
    private String name;

    @NotBlank(message = "El slug es obligatorio.")
    @Pattern(
            regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
            message = "The slug must use lowercase letters and hyphens."
    )
    private String slug;

    @NotNull(message = "El dueño de la empresa es obligatorio.")
    private UUID ownerId;

    private Boolean active;
}
