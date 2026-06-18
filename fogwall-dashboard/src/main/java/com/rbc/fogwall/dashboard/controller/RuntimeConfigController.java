package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.jetty.config.FogwallConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves runtime configuration to the SPA. This endpoint is public (no auth required) so the frontend can fetch it
 * before the user logs in — e.g. to learn the API base URL when the frontend is served from a different origin.
 *
 * <p>This is the git-proxy-java equivalent of the {@code runtime-config.json} file written by the Node.js git-proxy
 * {@code docker-entrypoint.sh}. Serving it from Spring means it works in all environments (Gradle run, Docker, bare
 * JAR) without needing to inject files into the static asset directory at container startup.
 */
@Tag(name = "System", description = "Health check and API metadata")
@RestController
@RequestMapping("/api")
public class RuntimeConfigController {

    @Autowired
    private FogwallConfig fogwallConfig;

    @Operation(operationId = "getRuntimeConfig", summary = "Get frontend runtime configuration")
    @GetMapping("/runtime-config")
    public Map<String, Object> runtimeConfig() {
        List<String> allowedOrigins = fogwallConfig.getServer().getAllowedOrigins();
        String authProvider = fogwallConfig.getAuth().getProvider();
        boolean bulkReview = fogwallConfig.getDashboard().isBulkReview();
        return Map.of("allowedOrigins", allowedOrigins, "authProvider", authProvider, "bulkReview", bulkReview);
    }
}
