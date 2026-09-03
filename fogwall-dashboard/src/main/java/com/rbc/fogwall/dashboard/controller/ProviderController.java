package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.config.AttestationQuestion;
import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.jetty.reload.ConfigHolder;
import com.rbc.fogwall.provider.ProviderRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Providers", description = "Configured upstream git providers")
@RestController
public class ProviderController {

    @Resource(name = "providers")
    private ProviderRegistry providers;

    @Autowired
    private ConfigHolder configHolder;

    @Resource(name = "fogwallConfig")
    private FogwallConfig fogwallConfig;

    @Operation(operationId = "listProviders", summary = "List configured providers")
    @GetMapping("/api/providers")
    public List<ProviderInfo> list() {
        // Attestation questions are global — every provider in the response carries the same list.
        // The per-provider API shape is preserved to avoid churning the frontend if we add per-provider variants later.
        List<AttestationQuestion> attestations = configHolder.getAttestations();
        boolean requireReviewPermission = fogwallConfig.getServer().isRequireReviewPermission();
        // SSH transport is available for a provider only when the global SSH listener is on AND that provider entry
        // opts into SSH (#531's getSshUri()). The listener port and the provider's route path (servletPath, keyed on
        // the provider's HTTP host) are all the frontend needs to build ssh://host:port/<route>/<owner>/<repo>.git.
        boolean sshServerEnabled = fogwallConfig.getServer().getSsh().isEnabled();
        int sshPort = fogwallConfig.getServer().getSsh().getPort();
        return providers.getProviders().stream()
                .map(p -> new ProviderInfo(
                        p.getName(),
                        p.getProviderId(),
                        p.getUri().toString(),
                        p.getUri().getHost(),
                        "/push" + p.servletPath(),
                        "/proxy" + p.servletPath(),
                        sshServerEnabled && p.getSshUri().isPresent(),
                        sshPort,
                        p.servletPath(),
                        attestations,
                        requireReviewPermission))
                .toList();
    }

    public record ProviderInfo(
            String name,
            String id,
            String uri,
            String host,
            String pushPath,
            String proxyPath,
            boolean sshEnabled,
            int sshPort,
            String sshPath,
            List<AttestationQuestion> attestationQuestions,
            boolean requireReviewPermission) {}
}
