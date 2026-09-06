package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class GraphQlMutationParserTest {

    @Test
    void extractsSingleMutationField() {
        String query =
                "mutation IssueCreate($input: CreateIssueInput!) {" + " createIssue(input: $input) { issue { id } } }";
        assertEquals(List.of("createIssue"), GraphQlMutationParser.extractMutationFields(query));
    }

    @Test
    void queryOnlyDocument_returnsEmpty() {
        String query = "query { viewer { login } }";
        assertEquals(List.of(), GraphQlMutationParser.extractMutationFields(query));
    }

    @Test
    void clientAlias_doesNotHideOrForgeTheSchemaFieldName() {
        // A client alias ("notAMutation:") must not change what the allowlist sees — the schema field name
        // (createIssue) is what gets returned, never the alias.
        String query = "mutation { notAMutation: createIssue(input: {repositoryId: \"R_1\"}) { issue { id } } }";
        assertEquals(List.of("createIssue"), GraphQlMutationParser.extractMutationFields(query));
    }

    @Test
    void multipleTopLevelMutationSelections_returnsAllFields() {
        String query = "mutation { createIssue(input: {repositoryId: \"R_1\"}) { issue { id } }"
                + " addComment(input: {subjectId: \"I_1\", body: \"hi\"}) { commentEdge { node { id } } } }";
        assertEquals(List.of("createIssue", "addComment"), GraphQlMutationParser.extractMutationFields(query));
    }

    @Test
    void malformedGraphQl_throwsParseException() {
        String query = "mutation { createIssue(input: {";
        assertThrows(GraphQlParseException.class, () -> GraphQlMutationParser.extractMutationFields(query));
    }

    @Test
    void mutationNameEmbeddedOnlyInStringLiteral_isNotExtracted() {
        // A query-type document with "createIssue" appearing only as a string literal argument must not be
        // treated as a mutation — this is exactly the substring-match spoof the AST-based parser must reject.
        String query = "query { search(query: \"createIssue\") { nodes { __typename } } }";
        assertEquals(List.of(), GraphQlMutationParser.extractMutationFields(query));
    }
}
