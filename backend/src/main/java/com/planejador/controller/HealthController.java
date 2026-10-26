package com.planejador.controller;

import com.planejador.dto.DatabaseHealthResponse;
import com.planejador.dto.HealthResponse;
import com.planejador.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse(true, "UP");
    }

    @GetMapping("/database")
    public DatabaseHealthResponse database() {
        return healthService.checkDatabase();
    }
}
