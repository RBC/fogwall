package com.rbc.fogwall.scmapi;

import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Reads a plain string field out of an already-parsed JSON tree — shared by the dialect-specific extractors that
 * {@link HeadCommitValidator}'s callers use to find the head ref a pull/merge request create body names
 * ({@code source_branch}, {@code head}, or GitHub's nested {@code variables.input.headRefName}).
 */
public final class JsonBodyField {

    private JsonBodyField() {}

    /**
     * Empty when {@code node} is missing, absent, or not a string — {@link JsonNode#path} makes chained lookups
     * (GitHub's {@code variables.input.headRefName}) null-safe without a check at each step.
     */
    public static Optional<String> stringField(JsonNode node, String field) {
        if (node == null) {
            return Optional.empty();
        }
        JsonNode value = node.path(field);
        return value.isString() ? Optional.of(value.asString()) : Optional.empty();
    }
}
