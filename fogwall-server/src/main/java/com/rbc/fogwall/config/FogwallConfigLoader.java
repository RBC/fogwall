package com.rbc.fogwall.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.github.gestalt.config.Gestalt;
import org.github.gestalt.config.builder.GestaltBuilder;
import org.github.gestalt.config.exceptions.GestaltException;
import org.github.gestalt.config.source.MapConfigSourceBuilder;
import org.github.gestalt.config.source.StringConfigSourceBuilder;
import org.github.gestalt.config.yaml.YamlModuleConfigBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLAnchorReplayingFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Loads {@link FogwallConfig} from layered YAML files and environment variable overrides.
 *
 * <p>Layers, lowest to highest priority:
 *
 * <ol>
 *   <li>The bundled defaults — {@code defaults-fogwall.yml} in the jar, always loaded. Named outside the
 *       {@code fogwall-{profile}.yml} pattern, so no profile name resolves to it.
 *   <li>{@code fogwall.yml} — the default config file, loaded without a profile name when present on the classpath
 *       ({@code /app/conf} in the image, {@code config/} in local development)
 *   <li>Profile configs named in {@code FOGWALL_CONFIG_PROFILES} — comma-separated; each loads
 *       {@code fogwall-{profile}.yml} from the classpath in order, later profiles taking priority. A name with no
 *       matching file fails startup.
 *   <li>Environment variables with the {@code FOGWALL_} prefix
 * </ol>
 *
 * <p>Hot reload sits above all of these; see {@link #composeReload}.
 *
 * <p>File layers are merged by fogwall rather than by Gestalt, with one rule: <b>a mapping merges key by key; a list or
 * a scalar is replaced by the last layer that sets it.</b> Gestalt's own cross-source merge combines lists position by
 * position, so a shorter list from a higher layer would keep the lower layer's trailing entries, and each entry would
 * inherit any field it leaves unset from the entry at the same position below it. The merged document is handed to
 * Gestalt as a single source for binding, with environment variables layered on top — those are scalars, which
 * Gestalt's merge handles correctly.
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

    private static final String BUNDLED_DEFAULTS = "defaults-fogwall.yml";
    private static final String DEFAULT_CONFIG_FILE_NAME = "fogwall.yml";
    private static final String ENV_PREFIX = "FOGWALL_";
    private static final String PROFILES_ENV_VAR = "FOGWALL_CONFIG_PROFILES";

    // Parses every layer and backs Gestalt's own YamlLoader. A plain YAMLMapper doesn't expand a YAML alias resolving
    // to a collection before the binder sees it (FasterXML/jackson-dataformats-text#98); YAMLAnchorReplayingFactory
    // does, so an anchored list shared across config sections resolves instead of landing as the literal alias name.
    private static final YAMLMapper YAML =
            YAMLMapper.builder(new YAMLAnchorReplayingFactory()).build();

    private FogwallConfigLoader() {}

    /**
     * Loads and merges configuration from all sources, with the profiles named in {@code FOGWALL_CONFIG_PROFILES}.
     *
     * @return fully-populated {@link FogwallConfig}
     * @throws GestaltException if a layer cannot be parsed or a named profile is not on the classpath
     */
    public static FogwallConfig load() throws GestaltException {
        return load(System.getenv(PROFILES_ENV_VAR));
    }

    /**
     * As {@link #load()}, choosing the profiles explicitly rather than inheriting {@code FOGWALL_CONFIG_PROFILES} — for
     * tests, which cannot set an environment variable in their own process and must not be steered by one a developer
     * exported for {@code run}.
     *
     * @param profiles comma-separated profile names, or null/blank for none
     */
    public static FogwallConfig load(String profiles) throws GestaltException {
        return loadLayers(profiles, List.of()).getConfig();
    }

    /**
     * As {@link #load()}, with one more file layered above the profiles.
     *
     * @param overrideFile path to the external YAML file to layer on top
     */
    public static FogwallConfig loadWithOverride(Path overrideFile) throws GestaltException {
        return loadLayers(System.getenv(PROFILES_ENV_VAR), List.of(overrideFile))
                .getConfig();
    }

    /** Loads the configuration a process starts with, keeping its layers for hot reload to compose onto. */
    public static LoadedConfig loadLayers() throws GestaltException {
        return loadLayers(System.getenv(PROFILES_ENV_VAR), List.of());
    }

    /**
     * As {@link #loadLayers()}, choosing the profiles explicitly and layering further files above them.
     *
     * <p>The pairing a test fixture needs: a committed profile for everything that is fixed, and a generated file on
     * top for what cannot be known until the process is running — the port an upstream container bound, the temporary
     * directory a database lives in.
     *
     * @param profiles comma-separated profile names, or null/blank for none
     * @param extraFiles further YAML files layered above the profiles, lowest priority first
     */
    public static LoadedConfig loadLayers(String profiles, List<Path> extraFiles) throws GestaltException {
        return loadLayers(profiles, extraFiles, System.getenv());
    }

    /**
     * As {@link #loadLayers(String, List)}, reading {@code FOGWALL_} overrides from {@code environment} rather than the
     * process environment — for tests.
     */
    public static LoadedConfig loadLayers(String profiles, List<Path> extraFiles, Map<String, String> environment)
            throws GestaltException {
        return loadLayers(DEFAULT_CONFIG_FILE_NAME, profiles, extraFiles, environment);
    }

    static LoadedConfig loadLayers(
            String defaultConfigFileName, String profiles, List<Path> extraFiles, Map<String, String> environment)
            throws GestaltException {
        ObjectNode tree = readClasspathLayer(BUNDLED_DEFAULTS)
                .orElseThrow(() -> new GestaltException(BUNDLED_DEFAULTS + " is missing from the classpath"));
        log.info("Loaded bundled defaults from {}", BUNDLED_DEFAULTS);

        var defaultConfigFile = readClasspathLayer(defaultConfigFileName);
        if (defaultConfigFile.isPresent()) {
            tree = merge(tree, defaultConfigFile.get());
            log.info("Loaded configuration from {}", defaultConfigFileName);
        }

        for (String profileResource : profileResources(profiles)) {
            tree = merge(tree, readClasspathLayer(profileResource).orElseThrow());
            log.info("Loaded profile configuration from {}", profileResource);
        }

        for (Path file : extraFiles) {
            tree = merge(tree, readFileLayer(file));
            log.info("Loaded configuration from {}", file);
        }

        Map<String, String> envOverrides = buildEnvOverrides(environment);
        if (!envOverrides.isEmpty()) {
            log.info("Applied {} environment variable override(s) with prefix {}", envOverrides.size(), ENV_PREFIX);
        }

        return new LoadedConfig(bind(tree, envOverrides), tree, envOverrides);
    }

    /**
     * Reads a hot reload document: structurally validated against the config schema, anchors resolved, keys normalized
     * the way every other layer is.
     *
     * <p>A file that is empty, is not valid YAML, is not a mapping at the top level, or names a key the schema doesn't
     * have fails validation with an unchecked exception.
     *
     * @throws GestaltException if the file cannot be read
     */
    public static ObjectNode readReloadDocument(Path file) throws GestaltException {
        return readFileLayer(file);
    }

    /**
     * The configuration after a hot reload: {@code reloadTree} merged onto the file layers the process started with, by
     * the same rule the file layers were merged with, then bound with the startup environment variable overrides.
     *
     * <p>The reload document takes priority over environment variables. An override for a path the reload document sets
     * is dropped, with a warning naming the path, so the value that applies is never ambiguous; an override for any
     * other path still applies.
     *
     * <p>Composition always starts from {@code startup}, never from a previous reload.
     */
    public static FogwallConfig composeReload(LoadedConfig startup, ObjectNode reloadTree) throws GestaltException {
        ObjectNode reload = normalize(reloadTree);
        ObjectNode tree = merge(startup.getFileTree(), reload);
        Map<String, String> envOverrides = new HashMap<>();
        startup.getEnvironment().forEach((path, value) -> {
            if (setsPath(reload, path)) {
                log.warn(
                        "Config reload: {} is set by both an environment variable and the reload document — "
                                + "the reload document's value applies",
                        path);
            } else {
                envOverrides.put(path, value);
            }
        });
        return bind(tree, envOverrides);
    }

    /**
     * Resolves each profile named in {@code profilesEnv} to its classpath resource, in order.
     *
     * <p>A profile is a file the operator supplies — mounted into {@code /app/conf} in the image, or the
     * {@code config/} directory the run tasks put on the classpath in local development. A name that resolves to
     * nothing is a misconfiguration rather than something to carry on from: fogwall would start on base defaults with
     * no providers, no users and no rules, and the first sign of it would be traffic being refused for reasons nothing
     * explains.
     *
     * @param profilesEnv the raw comma-separated value, or null/blank for no profiles
     * @throws GestaltException if a named profile is not on the classpath
     */
    static List<String> profileResources(String profilesEnv) throws GestaltException {
        List<String> resources = new ArrayList<>();
        if (profilesEnv == null || profilesEnv.isBlank()) {
            return resources;
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
            resources.add(profileConfig);
        }
        return resources;
    }

    /**
     * Merges {@code overlay} onto {@code base} without modifying either: a mapping merges key by key; a list or a
     * scalar from {@code overlay} replaces whatever {@code base} has at that key. A null in {@code overlay} leaves the
     * key unset rather than clearing it.
     */
    static ObjectNode merge(ObjectNode base, ObjectNode overlay) {
        ObjectNode result = base.deepCopy();
        for (Map.Entry<String, JsonNode> entry : overlay.properties()) {
            JsonNode value = entry.getValue();
            if (value.isNull()) {
                continue;
            }
            JsonNode existing = result.get(entry.getKey());
            if (existing instanceof ObjectNode existingObject && value instanceof ObjectNode valueObject) {
                result.set(entry.getKey(), merge(existingObject, valueObject));
            } else {
                result.set(entry.getKey(), value.deepCopy());
            }
        }
        return result;
    }

    /**
     * Whether {@code tree} sets a value at {@code path} (dot-separated, as environment overrides are expressed) or at
     * any ancestor of it.
     */
    static boolean setsPath(ObjectNode tree, String path) {
        JsonNode node = tree;
        for (String segment : path.split("\\.")) {
            if (!(node instanceof ObjectNode object)) {
                return true;
            }
            JsonNode child = object.get(segment);
            if (child == null || child.isNull()) {
                return false;
            }
            node = child;
        }
        return true;
    }

    private static FogwallConfig bind(ObjectNode tree, Map<String, String> envOverrides) throws GestaltException {
        var builder = new GestaltBuilder()
                .setTreatMissingValuesAsErrors(false)
                .setTreatMissingDiscretionaryValuesAsErrors(false)
                // Registering a custom decoder suppresses Gestalt's auto-added default decoders, so add them back
                // explicitly alongside the ones for the content-block rule shorthand.
                .addDefaultDecoders()
                .addDecoder(new MatchRuleSettingsDecoder())
                .addDecoder(new BlockSettingDecoder())
                .addModuleConfig(
                        YamlModuleConfigBuilder.builder().setObjectMapper(YAML).build());

        builder.addSource(StringConfigSourceBuilder.builder()
                .setConfig(YAML.writeValueAsString(tree))
                .setFormat("yml")
                .build());
        if (!envOverrides.isEmpty()) {
            builder.addSource(MapConfigSourceBuilder.builder()
                    .setCustomConfig(envOverrides)
                    .build());
        }

        Gestalt gestalt = builder.build();
        gestalt.loadConfigs();
        return gestalt.getConfig("", FogwallConfig.class);
    }

    private static Optional<ObjectNode> readClasspathLayer(String resource) throws GestaltException {
        if (FogwallConfigLoader.class.getClassLoader().getResource(resource) == null) {
            return Optional.empty();
        }
        YamlStructureValidator.validateClasspathResource(resource);
        try (InputStream in = FogwallConfigLoader.class.getClassLoader().getResourceAsStream(resource)) {
            return Optional.of(parse(in));
        } catch (IOException e) {
            throw new GestaltException("Failed to read config resource: " + resource, e);
        }
    }

    private static ObjectNode readFileLayer(Path file) throws GestaltException {
        try {
            YamlStructureValidator.validateFile(file);
            try (InputStream in = Files.newInputStream(file)) {
                return parse(in);
            }
        } catch (IOException e) {
            throw new GestaltException("Cannot read config file: " + file, e);
        }
    }

    /**
     * Parses a layer {@link YamlStructureValidator} has already accepted, which rules out malformed YAML, an empty
     * document and a top level that is not a mapping.
     */
    private static ObjectNode parse(InputStream in) {
        return normalize((ObjectNode) YAML.readTree(in));
    }

    /**
     * Lowercases every mapping key, as Gestalt does when it binds, so the same key written in two layers merges no
     * matter its case.
     */
    private static ObjectNode normalize(ObjectNode node) {
        ObjectNode result = YAML.createObjectNode();
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            result.set(entry.getKey().toLowerCase(Locale.ROOT), normalizeValue(entry.getValue()));
        }
        return result;
    }

    private static JsonNode normalizeValue(JsonNode value) {
        if (value instanceof ObjectNode object) {
            return normalize(object);
        }
        if (value instanceof ArrayNode array) {
            ArrayNode result = YAML.createArrayNode();
            for (int i = 0; i < array.size(); i++) {
                result.add(normalizeValue(array.get(i)));
            }
            return result;
        }
        return value;
    }

    private static Map<String, String> buildEnvOverrides(Map<String, String> environment) {
        Map<String, String> overrides = new HashMap<>();
        environment.forEach((varName, varValue) -> {
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
