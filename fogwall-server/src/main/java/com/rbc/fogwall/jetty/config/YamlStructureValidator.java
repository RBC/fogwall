package com.rbc.fogwall.jetty.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Validates YAML config files against the known {@link FogwallConfig} structure before Gestalt loads them. Any key that
 * does not correspond to a field in the target POJO throws {@link IllegalStateException} at startup, so typos and
 * misplaced keys are caught immediately instead of being silently ignored.
 *
 * <p>Uses Jackson's {@code FAIL_ON_UNKNOWN_PROPERTIES} feature with kebab-case property name mapping, which matches the
 * YAML key convention used throughout git-proxy.yml.
 */
@Slf4j
final class YamlStructureValidator {

    private static final YAMLMapper MAPPER = YAMLMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private YamlStructureValidator() {}

    static void validateClasspathResource(String resource) {
        InputStream is = YamlStructureValidator.class.getClassLoader().getResourceAsStream(resource);
        if (is == null) return;
        try (is) {
            validate(is, resource);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read config resource: " + resource, e);
        }
    }

    static void validateFile(Path file) throws IOException {
        try (InputStream is = Files.newInputStream(file)) {
            validate(is, file.toString());
        }
    }

    private static void validate(InputStream is, String source) throws IOException {
        try {
            MAPPER.readValue(is, FogwallConfig.class);
        } catch (UnrecognizedPropertyException e) {
            // getPathReference() produces e.g. "fogwallConfig[\"commit\"]->CommitSettings[\"diff\"]"
            throw new IllegalStateException(
                    "Unknown configuration key '"
                            + e.getPropertyName()
                            + "' in "
                            + source
                            + " (path: "
                            + e.getPathReference()
                            + "). Check for a typo or misplaced key."
                            + " See docs/CONFIGURATION.md for valid keys.",
                    e);
        }
    }
}
