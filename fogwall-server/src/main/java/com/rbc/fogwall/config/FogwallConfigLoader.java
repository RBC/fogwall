package com.rbc.fogwall.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLAnchorReplayingFactory;
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
import org.github.gestalt.config.yaml.YamlModuleConfigBuilder;

/**
 * Loads {@link FogwallConfig} from YAML files and environment variable overrides using Gestalt.
 *
 * <p>Source priority (lowest → highest):
 *
 * <ol>
 *   <li>{@code fogwall.yml} — base defaults shipped with the jar
 *   <li>Profile configs named in {@code FOGWALL_CONFIG_PROFILES} — comma-separated list of profile names; each loads
 *       {@code fogwall-{profile}.yml} from the classpath in order. Later profiles take priority over earlier ones, and
 *       a name with no matching file fails startup.
 *   <li>Environment variables with {@code FOGWALL_} prefix (highest priority)
 * </ol>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code FOGWALL_CONFIG_PROFILES=local} — local dev (loads {@code fogwall-local.yml})
 *   <li>{@code FOGWALL_CONFIG_PROFILES=docker-default,ldap} — Docker + LDAP auth
 * </ul>
 *
 * <p>Environment variable naming: two conventions are supported.
 *
 * <p><b>Legacy (single-underscore) convention</b> — strip the {@code FOGWALL_} prefix, lowercase, replace {@code _}
 * with {@code .}. Works for paths that contain no hyphens:
 *
 * <ul>
 *   <li>{@code FOGWALL_SERVER_PORT=9090} → {@code server.port}
 *   <li>{@code FOGWALL_DATABASE_TYPE=postgres} → {@code database.type}
 *   <li>{@code FOGWALL_PROVIDERS_GITHUB_ENABLED=false} → {@code providers.github.enabled}
 * </ul>
 *
 * <p><b>Double-underscore convention</b> — activated when the variable name contains {@code __}. {@code __} maps to
 * {@code .} (path separator) and {@code _} maps to {@code -} (hyphen within a segment). Use this for hyphenated keys or
 * hyphenated provider IDs:
 *
 * <ul>
 *   <li>{@code FOGWALL_PROVIDERS__GITEA_SSH__API_TOKEN} → {@code providers.gitea-ssh.api-token}
 *   <li>{@code FOGWALL_PROVIDERS__GITHUB__ENABLED=false} → {@code providers.github.enabled}
 * </ul>
 */
@Slf4j
public final class FogwallConfigLoader {

    private static final String BASE_CONFIG = "fogwall.yml";
    private static final String ENV_PREFIX = "FOGWALL_";
    private static final String PROFILES_ENV_VAR = "FOGWALL_CONFIG_PROFILES";

    // Gestalt's own YamlLoader defaults to a plain ObjectMapper(YAMLFactory), which doesn't expand a YAML alias
    // resolving to a collection before the POJO binder sees it (FasterXML/jackson-dataformats-text#98). Registering
    // this module config replaces it with one built on YAMLAnchorReplayingFactory, same fix as YamlStructureValidator
    // and LiveConfigLoader, so an anchored list shared across config sections resolves instead of landing as the
    // literal alias name.
    private static final ObjectMapper ANCHOR_AWARE_YAML_MAPPER =
            new ObjectMapper(new YAMLAnchorReplayingFactory()).findAndRegisterModules();

    private FogwallConfigLoader() {}

    /**
     * Loads and merges configuration from all sources.
     *
     * @return fully-populated {@link FogwallConfig}
     * @throws GestaltException if the base config cannot be parsed
     */
    public static FogwallConfig load() throws GestaltException {
        return load(System.getenv(PROFILES_ENV_VAR));
    }

    /**
     * Loads and merges configuration from the base file, the named profiles and environment variable overrides,
     * choosing the profiles explicitly rather than inheriting {@code FOGWALL_CONFIG_PROFILES}.
     *
     * <p>For callers that compose their own profile set — tests, chiefly, which cannot set an environment variable in
     * their own process and must not be steered by one a developer exported for {@code run}.
     *
     * @param profiles comma-separated profile names, or null/blank for none
     * @return fully-populated {@link FogwallConfig}
     * @throws GestaltException if the base config cannot be parsed, or a named profile is not on the classpath
     */
    public static FogwallConfig load(String profiles) throws GestaltException {
        YamlStructureValidator.validateClasspathResource(BASE_CONFIG);

        var builder = new GestaltBuilder()
                .setTreatMissingValuesAsErrors(false)
                .setTreatMissingDiscretionaryValuesAsErrors(false)
                .addModuleConfig(YamlModuleConfigBuilder.builder()
                        .setObjectMapper(ANCHOR_AWARE_YAML_MAPPER)
                        .build());

        builder.addSource(
                ClassPathConfigSourceBuilder.builder().setResource(BASE_CONFIG).build());
        log.info("Loaded base configuration from {}", BASE_CONFIG);

        addProfileSources(builder, profiles, true);

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
        return loadWithOverride(System.getenv(PROFILES_ENV_VAR), overrideFile);
    }

    /**
     * As {@link #loadWithOverride(Path)}, choosing the profiles explicitly rather than inheriting
     * {@code FOGWALL_CONFIG_PROFILES}.
     *
     * <p>The pairing a test fixture needs: a committed profile for everything that is fixed, and a generated file on
     * top for what cannot be known until the process is running — the port an upstream container bound, the temporary
     * directory a database lives in.
     *
     * @param profiles comma-separated profile names, or null/blank for none
     * @param overrideFile path to the external YAML file to overlay
     * @return fully-populated {@link FogwallConfig} with the override applied
     * @throws GestaltException if the base or override config cannot be parsed
     */
    public static FogwallConfig loadWithOverride(String profiles, Path overrideFile) throws GestaltException {
        try {
            YamlStructureValidator.validateFile(overrideFile);
        } catch (IOException e) {
            throw new GestaltException("Cannot read override config file: " + overrideFile, e);
        }

        var builder = new GestaltBuilder()
                .setTreatMissingValuesAsErrors(false)
                .setTreatMissingDiscretionaryValuesAsErrors(false)
                .addModuleConfig(YamlModuleConfigBuilder.builder()
                        .setObjectMapper(ANCHOR_AWARE_YAML_MAPPER)
                        .build());

        builder.addSource(
                ClassPathConfigSourceBuilder.builder().setResource(BASE_CONFIG).build());

        addProfileSources(builder, profiles, false);

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

    /**
     * Adds a source for each profile named in {@code FOGWALL_CONFIG_PROFILES}, in order, so that later profiles take
     * priority over earlier ones.
     *
     * <p>A profile is a file the operator supplies — mounted into {@code /app/conf} in the image, or the {@code conf/}
     * directory the run tasks put on the classpath in local development. A name that resolves to nothing is a
     * misconfiguration rather than something to carry on from: fogwall would start on base defaults with no providers,
     * no users and no rules, and the first sign of it would be traffic being refused for reasons nothing explains.
     *
     * @param profilesEnv the raw comma-separated value, or null/blank for no profiles
     * @param validate whether to run structural validation on each profile, as the initial load does
     * @throws GestaltException if a named profile is not on the classpath
     */
    static void addProfileSources(GestaltBuilder builder, String profilesEnv, boolean validate)
            throws GestaltException {
        if (profilesEnv == null || profilesEnv.isBlank()) {
            return;
        }
        for (String profile : profilesEnv.split(",")) {
            String name = profile.trim();
            if (name.isEmpty()) {
                continue;
            }
            String profileConfig = "fogwall-" + name + ".yml";
            if (FogwallConfigLoader.class.getClassLoader().getResource(profileConfig) == null) {
                throw new GestaltException(PROFILES_ENV_VAR + " names the profile '" + name + "' but " + profileConfig
                        + " is not on the classpath. Supply the file — config/" + profileConfig
                        + " in local development, /app/conf/" + profileConfig
                        + " in a container — or remove the name from " + PROFILES_ENV_VAR + ".");
            }
            if (validate) {
                YamlStructureValidator.validateClasspathResource(profileConfig);
            }
            builder.addSource(ClassPathConfigSourceBuilder.builder()
                    .setResource(profileConfig)
                    .build());
            log.info("Loaded profile configuration from {}", profileConfig);
        }
    }

    private static Map<String, String> buildEnvOverrides() {
        Map<String, String> overrides = new HashMap<>();
        System.getenv().forEach((varName, varValue) -> {
            if (varName.startsWith(ENV_PREFIX) && !varName.equals(PROFILES_ENV_VAR)) {
                String configPath = envVarToConfigPath(varName);
                overrides.put(configPath, varValue);
                log.debug("Env override: {} → {}", varName, configPath);
            }
        });
        return overrides;
    }

    static String envVarToConfigPath(String varName) {
        String stripped = varName.substring(ENV_PREFIX.length()).toLowerCase();
        if (stripped.contains("__")) {
            // double-underscore convention: __ = path separator, _ = hyphen
            return String.join(".", stripped.split("__")).replace('_', '-');
        }
        // legacy single-underscore convention: _ = path separator
        return stripped.replace('_', '.');
    }
}
