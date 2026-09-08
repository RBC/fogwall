package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JsonBodyFieldTest {

    private static final JsonMapper MAPPER = new JsonMapper();

    @Test
    void topLevelStringField_isRead() {
        var node = MAPPER.readTree("{\"source_branch\":\"feature\"}");

        assertEquals(Optional.of("feature"), JsonBodyField.stringField(node, "source_branch"));
    }

    @Test
    void nestedField_isRead_viaPath() {
        var node = MAPPER.readTree("{\"input\":{\"headRefName\":\"owner:branch\"}}")
                .path("input");

        assertEquals(Optional.of("owner:branch"), JsonBodyField.stringField(node, "headRefName"));
    }

    @Test
    void missingField_isEmpty() {
        var node = MAPPER.readTree("{}");

        assertTrue(JsonBodyField.stringField(node, "head").isEmpty());
    }

    @Test
    void nonStringField_isEmpty() {
        var node = MAPPER.readTree("{\"head\":123}");

        assertTrue(JsonBodyField.stringField(node, "head").isEmpty());
    }

    @Test
    void nullNode_isEmpty() {
        assertTrue(JsonBodyField.stringField(null, "head").isEmpty());
    }
}
