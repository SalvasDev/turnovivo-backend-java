package com.saldev.turnovivo.controller;

import com.saldev.turnovivo.dto.request.AdminUserCreateRequest;
import com.saldev.turnovivo.dto.response.UserResponse;
import com.saldev.turnovivo.model.enums.Role;
import com.saldev.turnovivo.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<UserResponse> list(@RequestParam(required = false) Role role) {
        return userService.listUsers(role);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createAdmin(@Valid @RequestBody AdminUserCreateRequest request) {
        return userService.createAdmin(request);
    }
}
