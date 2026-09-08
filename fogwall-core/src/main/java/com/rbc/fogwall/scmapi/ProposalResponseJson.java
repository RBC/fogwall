package com.rbc.fogwall.scmapi;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Null-tolerant field access over an upstream response, shared by the dialect readers. */
final class ProposalResponseJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Pattern NUMBER_IN_PATH = Pattern.compile("/(?:issues|merge_requests|pulls)/(\\d+)(?:/|$)");
    private static final Pattern NUMBER_AT_END = Pattern.compile("/(\\d+)/?$");

    private ProposalResponseJson() {}

    /** The body as an object, or null when it is absent, malformed, or not an object. */
    static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) && node.get(field).isValueNode()
                ? node.get(field).asText()
                : null;
    }

    static Integer integer(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) && node.get(field).canConvertToInt()
                ? node.get(field).asInt()
                : null;
    }

    /** The number in {@code /issues/{n}}, {@code /merge_requests/{n}} or {@code /pulls/{n}} of a request path. */
    static Integer numberInPath(String path) {
        if (path == null) {
            return null;
        }
        Matcher m = NUMBER_IN_PATH.matcher(path);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    /** The trailing number of a URL such as {@code https://github.com/acme/widgets/pull/7}. */
    static Integer trailingNumber(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = NUMBER_AT_END.matcher(url);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }
}
