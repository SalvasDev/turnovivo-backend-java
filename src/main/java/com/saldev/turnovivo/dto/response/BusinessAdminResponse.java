package com.saldev.turnovivo.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record BusinessAdminResponse(
        UUID id,
        String name,
        String slug,
        UUID ownerId,
        String ownerEmail,
        boolean active,
        LocalDateTime createdAt
) {}
