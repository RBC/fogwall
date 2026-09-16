package com.rbc.fogwall.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.github.gestalt.config.decoder.Decoder;
import org.github.gestalt.config.decoder.DecoderContext;
import org.github.gestalt.config.decoder.Priority;
import org.github.gestalt.config.node.ConfigNode;
import org.github.gestalt.config.node.NodeType;
import org.github.gestalt.config.reflect.TypeCapture;
import org.github.gestalt.config.tag.Tags;
import org.github.gestalt.config.utils.GResultOf;

/**
 * Runtime (Gestalt) side of {@link BlockSetting}'s two shapes. A list node binds each element as a
 * {@link MatchRuleSettings} (delegated to {@link MatchRuleSettingsDecoder}, so the scalar-string shorthand works); a
 * map node reads the deprecated {@code literals}/{@code patterns} arrays and folds them into literal/regex matchers.
 */
public class BlockSettingDecoder implements Decoder<BlockSetting> {

    @Override
    public Priority priority() {
        return Priority.HIGH;
    }

    @Override
    public String name() {
        return "BlockSetting";
    }

    @Override
    public boolean canDecode(String path, Tags tags, ConfigNode node, TypeCapture<?> type) {
        return BlockSetting.class.equals(type.getRawType());
    }

    @Override
    public GResultOf<BlockSetting> decode(
            String path, Tags tags, ConfigNode node, TypeCapture<?> type, DecoderContext context) {
        List<MatchRuleSettings> matchers = new ArrayList<>();
        NodeType nodeType = node.getNodeType();

        if (nodeType == NodeType.ARRAY) {
            for (int i = 0; i < node.size(); i++) {
                ConfigNode element = node.getIndex(i).orElse(null);
                if (element == null) {
                    continue;
                }
                GResultOf<MatchRuleSettings> decoded = context.getDecoderService()
                        .decodeNode(
                                path + "[" + i + "]", tags, element, TypeCapture.of(MatchRuleSettings.class), context);
                if (decoded.hasResults()) {
                    matchers.add(decoded.results());
                }
            }
            return GResultOf.result(new BlockSetting(matchers, false));
        }

        if (nodeType == NodeType.MAP) {
            addStrings(node.getKey("literals"), "literal", matchers);
            addStrings(node.getKey("patterns"), "regex", matchers);
            return GResultOf.result(new BlockSetting(matchers, true));
        }

        // A bare scalar is taken as a single regex; anything else yields no matchers.
        if (node.hasValue()) {
            matchers.add(MatchRuleSettings.fromString(node.getValue().orElse("")));
        }
        return GResultOf.result(new BlockSetting(matchers, false));
    }

    private static void addStrings(Optional<ConfigNode> arrayNode, String match, List<MatchRuleSettings> out) {
        if (arrayNode.isEmpty()) {
            return;
        }
        ConfigNode array = arrayNode.get();
        for (int i = 0; i < array.size(); i++) {
            array.getIndex(i)
                    .flatMap(ConfigNode::getValue)
                    .ifPresent(value -> out.add(new MatchRuleSettings(match, value)));
        }
    }
}
