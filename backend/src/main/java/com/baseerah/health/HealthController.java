package com.baseerah.health;

import com.baseerah.common.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * App-level health check that proves the API envelope contract is alive (DESIGN.md §6).
 *
 * <p>Distinct from Actuator's {@code /actuator/health} (infra/DB liveness) — this returns the same
 * {@code {"status":"OK","data":...}} shape the Flutter client expects from every endpoint.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("service", "baseerah", "status", "UP"));
    }
}
