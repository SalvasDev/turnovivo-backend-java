package com.saldev.turnovivo.mapper;

import com.saldev.turnovivo.dto.request.UserRegisterRequest;
import com.saldev.turnovivo.model.entity.User;
import com.saldev.turnovivo.model.enums.Role;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    // Converts the incoming registration DTO into a database entity.
    // Uses Lombok's Builder pattern.
    public User toEntity(UserRegisterRequest request) {
        if(request == null) {
            return null;
        }
        return User.builder()
                .email(request.getEmail().trim().toLowerCase())
                .passwordHash(request.getPasswordHash())
                .role(Role.CUSTOMER)
                .build();
    }
}
