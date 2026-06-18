package com.rbc.fogwall.dashboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "System", description = "Health check and API metadata")
@RestController
@RequestMapping("/api")
public class HealthController {

    @Operation(operationId = "health", summary = "Health check")
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }

    @Operation(operationId = "apiInfo", summary = "API metadata and version")
    @GetMapping("/")
    public Map<String, Object> info() {
        return Map.of(
                "name",
                "fogwall",
                "version",
                "0.0.1-SNAPSHOT",
                "endpoints",
                Map.of("push", "/api/push", "health", "/api/health"));
    }
}
