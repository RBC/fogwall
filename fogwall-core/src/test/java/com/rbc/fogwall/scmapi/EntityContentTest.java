package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every extractor takes the whole parsed request body. Getting that wrong is silent — the wrong node yields no fields,
 * the inspector finds nothing to inspect, and the request forwards as though its content were clean — so each dialect
 * is pinned against a body shaped the way its CLI actually sends one.
 */
class EntityContentTest {

    private static final JsonMapper MAPPER = new JsonMapper();

    private static List<EntityContent> extract(
            java.util.function.Function<tools.jackson.databind.JsonNode, List<EntityContent>> extractor, String json) {
        return extractor.apply(MAPPER.readTree(json));
    }

    @Test
    void gitHubReadsThroughVariablesIntoInput() {
        var content = extract(EntityContent::fromGraphQlBody, """
                {"query":"mutation($input:CreatePullRequestInput!){createPullRequest(input:$input){id}}",
                 "variables":{"input":{"repositoryId":"R_1","title":"a title","body":"a body"}}}""");
        assertEquals(List.of(new EntityContent("title", "a title"), new EntityContent("body", "a body")), content);
    }

    @Test
    void gitLabReadsTitleDescriptionAndNoteBody() {
        assertEquals(
                List.of(new EntityContent("title", "t"), new EntityContent("description", "d")),
                extract(EntityContent::fromGitLabBody, "{\"title\":\"t\",\"description\":\"d\"}"));
        assertEquals(
                List.of(new EntityContent("body", "a comment")),
                extract(EntityContent::fromGitLabBody, "{\"body\":\"a comment\"}"));
    }

    @Test
    void forgejoReadsTitleAndBody() {
        assertEquals(
                List.of(new EntityContent("title", "t"), new EntityContent("body", "b")),
                extract(EntityContent::fromForgejoBody, "{\"title\":\"t\",\"body\":\"b\"}"));
    }

    @Test
    void ignoresMissingBlankAndNonStringFields() {
        assertTrue(extract(EntityContent::fromGitLabBody, "{\"title\":\"   \",\"description\":null}")
                .isEmpty());
        assertTrue(extract(EntityContent::fromGitLabBody, "{\"title\":42}").isEmpty());
        assertTrue(extract(EntityContent::fromGitLabBody, "{}").isEmpty());
        assertTrue(extract(EntityContent::fromGraphQlBody, "{\"query\":\"query{viewer{login}}\"}")
                .isEmpty());
    }

    /** A GraphQL body whose prose sits at the root, not under variables.input, carries nothing to inspect. */
    @Test
    void gitHubDoesNotReadProseFromTheWrongLevel() {
        assertTrue(extract(EntityContent::fromGraphQlBody, "{\"input\":{\"title\":\"t\",\"body\":\"b\"}}")
                .isEmpty());
    }
}
