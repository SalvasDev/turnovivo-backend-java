package com.saldev.turnovivo.mapper;

import com.saldev.turnovivo.dto.request.BusinessCreateRequest;
import com.saldev.turnovivo.dto.response.BusinessResponse;
import com.saldev.turnovivo.dto.response.BusinessAdminResponse;
import com.saldev.turnovivo.model.entity.Business;
import com.saldev.turnovivo.model.entity.User;
import org.springframework.stereotype.Component;

@Component
public class BusinessMapper {

    // Converts the creation DTO into a JPA entity ready to be stored in PostgreSQL.
    public Business toEntity(BusinessCreateRequest request, User owner) {
        if (request == null) {
            return null;
        }

        return Business.builder()
                .name(request.getName())
                .slug(request.getSlug().trim())
                .owner(owner)
                .build();
    }

    // Converts the database entity into a safe response DTO for the frontend.
    public BusinessResponse toResponse(Business business) {
        if (business == null) {
            return null;
        }

        return new BusinessResponse(
                business.getId(),
                business.getName(),
                business.getSlug(),
                business.getCreatedAt().toLocalDateTime()
        );
    }

    public BusinessAdminResponse toAdminResponse(Business business) {
        return new BusinessAdminResponse(
                business.getId(),
                business.getName(),
                business.getSlug(),
                business.getOwner().getId(),
                business.getOwner().getEmail(),
                business.isActive(),
                business.getCreatedAt().toLocalDateTime()
        );
    }
}
