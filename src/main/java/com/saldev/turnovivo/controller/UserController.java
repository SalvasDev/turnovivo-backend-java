package com.saldev.turnovivo.controller;

import com.saldev.turnovivo.dto.request.UserRegisterRequest;
import com.saldev.turnovivo.model.entity.User;
import com.saldev.turnovivo.dto.response.UserResponse;
import com.saldev.turnovivo.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /*
     * Registers a new customer or staff member into the system.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody UserRegisterRequest request) {
        return userService.register(request);
    }
}
