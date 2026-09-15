package com.rbc.fogwall.config;

import org.github.gestalt.config.decoder.Decoder;
import org.github.gestalt.config.decoder.DecoderContext;
import org.github.gestalt.config.decoder.Priority;
import org.github.gestalt.config.node.ConfigNode;
import org.github.gestalt.config.node.NodeType;
import org.github.gestalt.config.reflect.TypeCapture;
import org.github.gestalt.config.tag.Tags;
import org.github.gestalt.config.utils.GResultOf;

/**
 * Gestalt decoder that lets a {@link MatchRuleSettings} be written as either a mapping or a bare scalar. A leaf node (a
 * plain string in the {@code rules} list) becomes a regex rule over that string; a map node reads {@code match} and
 * {@code value} directly.
 *
 * <p>Registered ahead of Gestalt's default object decoder (which cannot bind a scalar into a POJO), so the shorthand
 * works at runtime. The Jackson structural validator handles the same shorthand separately via
 * {@link MatchRuleSettings#fromString}.
 */
public class MatchRuleSettingsDecoder implements Decoder<MatchRuleSettings> {

    @Override
    public Priority priority() {
        return Priority.HIGH;
    }

    @Override
    public String name() {
        return "MatchRuleSettings";
    }

    @Override
    public boolean canDecode(String path, Tags tags, ConfigNode node, TypeCapture<?> type) {
        return MatchRuleSettings.class.equals(type.getRawType());
    }

    @Override
    public GResultOf<MatchRuleSettings> decode(
            String path, Tags tags, ConfigNode node, TypeCapture<?> type, DecoderContext context) {
        if (node.getNodeType() == NodeType.LEAF) {
            return GResultOf.result(MatchRuleSettings.fromString(node.getValue().orElse("")));
        }
        String match = node.getKey("match").flatMap(ConfigNode::getValue).orElse("regex");
        String value = node.getKey("value").flatMap(ConfigNode::getValue).orElse("");
        return GResultOf.result(new MatchRuleSettings(match, value));
    }
}
