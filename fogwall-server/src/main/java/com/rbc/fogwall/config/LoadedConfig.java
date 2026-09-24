package com.rbc.fogwall.config;

import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The configuration a process started with, together with the layers it was bound from.
 *
 * <p>{@link #getConfig()} is what the server runs on. The file tree is the merged YAML of every file layer, before
 * environment variables, and the environment is the environment variable overrides as config paths. Hot reload
 * ({@link FogwallConfigLoader#composeReload}) composes onto these rather than onto {@link #getConfig()}, so a reload
 * document is merged key by key with the same rule the file layers were, and each reload starts again from what the
 * process started with instead of from the previous reload.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class LoadedConfig {

    @Getter
    private final FogwallConfig config;

    @Getter(AccessLevel.PACKAGE)
    private final ObjectNode fileTree;

    @Getter(AccessLevel.PACKAGE)
    private final Map<String, String> environment;

    /**
     * A configuration assembled in code rather than loaded from files. It has no file layers, so a hot reload composes
     * onto nothing: a section the reload document declares binds from the document alone.
     */
    public static LoadedConfig of(FogwallConfig config) {
        return new LoadedConfig(config, JsonNodeFactory.instance.objectNode(), Map.of());
    }
}
