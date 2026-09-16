package com.rbc.fogwall.config;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * Structural-validator (Jackson) side of {@link BlockSetting}'s two shapes: a list of matchers, or the deprecated
 * {@code { literals, patterns }} object. Runtime binding uses {@link BlockSettingDecoder} instead.
 */
public class BlockSettingDeserializer extends ValueDeserializer<BlockSetting> {

    @Override
    public BlockSetting deserialize(JsonParser p, DeserializationContext ctxt) {
        JsonNode node = ctxt.readTree(p);
        List<MatchRuleSettings> matchers = new ArrayList<>();

        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                matchers.add(ctxt.readTreeAsValue(node.get(i), MatchRuleSettings.class));
            }
            return new BlockSetting(matchers, false);
        }

        if (node.isObject()) {
            for (String name : node.propertyNames()) {
                if (!"literals".equals(name) && !"patterns".equals(name)) {
                    throw new IllegalStateException("Unknown key '" + name
                            + "' under a content 'block' object — expected 'literals' or 'patterns', or express "
                            + "'block' as a list of matchers. See the configuration reference.");
                }
            }
            JsonNode literals = node.get("literals");
            if (literals != null) {
                for (int i = 0; i < literals.size(); i++) {
                    matchers.add(
                            new MatchRuleSettings("literal", literals.get(i).asString()));
                }
            }
            JsonNode patterns = node.get("patterns");
            if (patterns != null) {
                for (int i = 0; i < patterns.size(); i++) {
                    matchers.add(new MatchRuleSettings("regex", patterns.get(i).asString()));
                }
            }
            return new BlockSetting(matchers, true);
        }

        return new BlockSetting(matchers, false);
    }
}
