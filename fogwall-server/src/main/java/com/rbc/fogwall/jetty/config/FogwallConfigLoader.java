package com.rbc.fogwall.jetty.config;

import com.rbc.fogwall.jetty.reload.LiveConfigLoader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.github.gestalt.config.Gestalt;
import org.github.gestalt.config.builder.GestaltBuilder;
import org.github.gestalt.config.exceptions.GestaltException;
import org.github.gestalt.config.source.ClassPathConfigSourceBuilder;
import org.github.gestalt.config.source.FileConfigSourceBuilder;
import org.github.gestalt.config.source.MapConfigSourceBuilder;

/**
 * Loads {@link FogwallConfig} from YAML files and environment variable overrides using Gestalt.
 *
 * <p>Source priority (lowest → highest):
 *
 * <ol>
 *   <li>{@code git-proxy.yml} — base defaults shipped with the jar
 *   <li>Profile configs named in {@code FOGWALL_CONFIG_PROFILES} — comma-separated list of profile names; each loads
 *       {@code git-proxy-{profile}.yml} from the classpath in order (optional, silently skipped if absent). Later
 *       profiles take priority over earlier ones.
 *   <li>Environment variables with {@code FOGWALL_} prefix (highest priority)
 * </ol>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code FOGWALL_CONFIG_PROFILES=local} — local dev (loads {@code git-proxy-local.yml})
 *   <li>{@code FOGWALL_CONFIG_PROFILES=docker-default,ldap} — Docker + LDAP auth
 * </ul>
 *
 * <p>Environment variable naming: strip the {@code FOGWALL_} prefix, lowercase, replace {@code _} with {@code .} to get
 * the config path. Examples:
 *
 * <ul>
 *   <li>{@code FOGWALL_SERVER_PORT=9090} → {@code server.port}
 *   <li>{@code FOGWALL_DATABASE_TYPE=postgres} → {@code database.type}
 *   <li>{@code FOGWALL_PROVIDERS_GITHUB_ENABLED=false} → {@code providers.github.enabled}
 * </ul>
 */
@Slf4j
public final class FogwallConfigLoader {

    private static final String BASE_CONFIG = "fogwall.yml";
    private static final String ENV_PREFIX = "FOGWALL_";
    private static final String PROFILES_ENV_VAR = "FOGWALL_CONFIG_PROFILES";

    private FogwallConfigLoader() {}

    /**
     * Loads and merges configuration from all sources.
     *
     * @return fully-populated {@link FogwallConfig}
     * @throws GestaltException if the base config cannot be parsed
     */
    public static FogwallConfig load() throws GestaltException {
        YamlStructureValidator.validateClasspathResource(BASE_CONFIG);

        var builder = new GestaltBuilder()
                .setTreatMissingValuesAsErrors(false)
                .setTreatMissingDiscretionaryValuesAsErrors(false);

        builder.addSource(
                ClassPathConfigSourceBuilder.builder().setResource(BASE_CONFIG).build());
        log.info("Loaded base configuration from {}", BASE_CONFIG);

        // Profile configs: FOGWALL_CONFIG_PROFILES=docker-default,ldap
        // loads git-proxy-docker-default.yml then git-proxy-ldap.yml (later = higher priority)
        String profilesEnv = System.getenv(PROFILES_ENV_VAR);
        if (profilesEnv != null && !profilesEnv.isBlank()) {
            for (String profile : profilesEnv.split(",")) {
                String profileConfig = "fogwall-" + profile.trim() + ".yml";
                if (FogwallConfigLoader.class.getClassLoader().getResource(profileConfig) != null) {
                    YamlStructureValidator.validateClasspathResource(profileConfig);
                    builder.addSource(ClassPathConfigSourceBuilder.builder()
                            .setResource(profileConfig)
                            .build());
                    log.info("Loaded profile configuration from {}", profileConfig);
                } else {
                    log.debug("Profile config {} not found on classpath (skipped)", profileConfig);
                }
            }
        }

        // Env var overrides: FOGWALL_SERVER_PORT → server.port
        Map<String, String> envOverrides = buildEnvOverrides();
        if (!envOverrides.isEmpty()) {
            builder.addSource(MapConfigSourceBuilder.builder()
                    .setCustomConfig(envOverrides)
                    .build());
            log.info("Applied {} environment variable override(s) with prefix {}", envOverrides.size(), ENV_PREFIX);
        }

        Gestalt gestalt = builder.build();
        gestalt.loadConfigs();

        return gestalt.getConfig("", FogwallConfig.class);
    }

    /**
     * Loads config from all standard sources (classpath base, profiles, env vars) plus an external override file. The
     * override file takes the highest priority — it is layered on top of everything else, including env vars.
     *
     * <p>Used by {@link LiveConfigLoader} to apply reloaded config from a watched filesystem path or a cloned git
     * repository without restarting the server.
     *
     * @param overrideFile path to the external YAML file to overlay
     * @return fully-populated {@link FogwallConfig} with the override applied
     * @throws GestaltException if the base or override config cannot be parsed
     */
    public static FogwallConfig loadWithOverride(Path overrideFile) throws GestaltException {
        try {
            YamlStructureValidator.validateFile(overrideFile);
        } catch (IOException e) {
            throw new GestaltException("Cannot read override config file: " + overrideFile, e);
        }

        var builder = new GestaltBuilder()
                .setTreatMissingValuesAsErrors(false)
                .setTreatMissingDiscretionaryValuesAsErrors(false);

        builder.addSource(
                ClassPathConfigSourceBuilder.builder().setResource(BASE_CONFIG).build());

        String profilesEnv = System.getenv(PROFILES_ENV_VAR);
        if (profilesEnv != null && !profilesEnv.isBlank()) {
            for (String profile : profilesEnv.split(",")) {
                String profileConfig = "fogwall-" + profile.trim() + ".yml";
                if (FogwallConfigLoader.class.getClassLoader().getResource(profileConfig) != null) {
                    builder.addSource(ClassPathConfigSourceBuilder.builder()
                            .setResource(profileConfig)
                            .build());
                }
            }
        }

        Map<String, String> envOverrides = buildEnvOverrides();
        if (!envOverrides.isEmpty()) {
            builder.addSource(MapConfigSourceBuilder.builder()
                    .setCustomConfig(envOverrides)
                    .build());
        }

        builder.addSource(
                FileConfigSourceBuilder.builder().setFile(overrideFile.toFile()).build());
        log.info("Applying reload override from {}", overrideFile);

        Gestalt gestalt = builder.build();
        gestalt.loadConfigs();
        return gestalt.getConfig("", FogwallConfig.class);
    }

    private static Map<String, String> buildEnvOverrides() {
        Map<String, String> overrides = new HashMap<>();
        System.getenv().forEach((varName, varValue) -> {
            if (varName.startsWith(ENV_PREFIX) && !varName.equals(PROFILES_ENV_VAR)) {
                String configPath =
                        varName.substring(ENV_PREFIX.length()).toLowerCase().replace('_', '.');
                overrides.put(configPath, varValue);
                log.debug("Env override: {} → {}", varName, configPath);
            }
        });
        return overrides;
    }
}
