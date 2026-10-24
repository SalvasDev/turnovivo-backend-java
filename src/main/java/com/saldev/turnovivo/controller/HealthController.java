package com.saldev.turnovivo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/")
    public Map<String, String> status() {
        return Map.of(
                "application", "turnovivo-backend",
                "status", "UP",
                "message", "API funcionando correctamente"
        );
    }
}
