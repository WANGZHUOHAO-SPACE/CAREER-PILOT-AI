package com.careerpilot.web;

import com.careerpilot.service.HealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;
    private final org.springframework.core.env.Environment environment;

    public HealthController(HealthService healthService) {
        this(healthService, new org.springframework.core.env.StandardEnvironment());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public HealthController(HealthService healthService, org.springframework.core.env.Environment environment) {
        this.healthService = healthService;
        this.environment = environment;
    }

    @GetMapping
    public ResponseEntity<HealthStatus> health() {
        boolean databaseUp = healthService.databaseAvailable();
        boolean vectorUp = healthService.vectorDatabaseAvailable();
        return ResponseEntity.status(databaseUp && vectorUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(new HealthStatus(databaseUp && vectorUp ? "UP" : "DOWN", "career-pilot-ai",
                        databaseUp ? "UP" : "DOWN", vectorUp ? "UP" : "DOWN",
                        environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local-test-embedding"))
                                ? "LOCAL_TEST" : "PRODUCTION"));
    }

    public record HealthStatus(String status, String application, String database, String vectorDatabase, String mode) {
    }
}
