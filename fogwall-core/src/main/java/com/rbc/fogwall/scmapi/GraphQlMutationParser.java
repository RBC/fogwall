package com.rbc.fogwall.scmapi;

import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.parser.Parser;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a GraphQL request's {@code query} document and extracts the schema field name(s) selected by every top-level
 * {@code mutation} operation.
 *
 * <p>Deliberately walks the parsed AST rather than matching text: a client-supplied alias, a string literal containing
 * a mutation name, or a batched second operation can all carry the same substring while the actual operation performed
 * differs. This is the security boundary the SCM API proxy allowlists against — see docs/internals/SCM_API_PROXY.md's
 * "Fail-closed allowlisting on the parsed operation" section.
 */
public final class GraphQlMutationParser {

    private GraphQlMutationParser() {}

    /**
     * Returns the schema mutation field name(s) selected by every {@code mutation} operation in {@code query}. Empty
     * when the document contains only {@code query} operations (reads) — those are not gated by the mutation allowlist
     * at all; see {@code GitHubMutationAllowlistFilter}.
     *
     * @throws GraphQlParseException if {@code query} is not syntactically valid GraphQL — callers must treat this as a
     *     deny, not a pass-through
     */
    public static List<String> extractMutationFields(String query) {
        Document document;
        try {
            document = Parser.parse(query);
        } catch (RuntimeException e) {
            // graphql-java throws several distinct unchecked exception types for malformed input
            // (InvalidSyntaxException and others) — caught broadly since any of them means the same
            // thing here: fail closed rather than let an unparseable document through.
            throw new GraphQlParseException("Malformed GraphQL document", e);
        }

        List<String> mutationFields = new ArrayList<>();
        for (Definition<?> definition : document.getDefinitions()) {
            if (!(definition instanceof OperationDefinition operation)) {
                continue;
            }
            if (operation.getOperation() != OperationDefinition.Operation.MUTATION) {
                continue;
            }
            for (Selection<?> selection : operation.getSelectionSet().getSelections()) {
                if (selection instanceof Field field) {
                    // Field#getName() is the schema field being selected (e.g. "createIssue"); a client-supplied
                    // alias lives in Field#getAlias() and must never be used for the allowlist decision.
                    mutationFields.add(field.getName());
                }
            }
        }
        return mutationFields;
    }
}
