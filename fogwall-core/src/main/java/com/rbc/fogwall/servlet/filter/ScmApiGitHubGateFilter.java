package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.ScmApiGateResponse.deny;
import static com.rbc.fogwall.servlet.ScmApiGateResponse.fail;

import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.scmapi.GitHubMutationAllowlist;
import com.rbc.fogwall.scmapi.GitHubNodeIdResolver;
import com.rbc.fogwall.scmapi.GraphQlMutationParser;
import com.rbc.fogwall.scmapi.GraphQlParseException;
import com.rbc.fogwall.scmapi.MutationNodeIdExtractor;
import com.rbc.fogwall.scmapi.MutationNodeIdRef;
import com.rbc.fogwall.scmapi.OwnerRepo;
import com.rbc.fogwall.scmapi.ScmApiGraphQlRequest;
import com.rbc.fogwall.scmapi.ScmApiGraphQlRequestParser;
import com.rbc.fogwall.servlet.RequestBodyWrapper;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import com.rbc.fogwall.servlet.ScmApiTokenExtractor;
import graphql.language.Field;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/**
 * The GraphQL dialect of {@link ScmApiGateFilter}, for {@code gh}. A mutation is allowlisted on the parsed AST, then
 * its opaque node ID is resolved to {@code owner/repo}; a pure {@code query} document is a read and forwards without a
 * permission check. The REST dialects share {@link ScmApiRestGateFilter} instead.
 */
@RequiredArgsConstructor
public class ScmApiGitHubGateFilter implements ScmApiGateFilter {

    static final String MERGE_OPERATION = "mergePullRequest";

    private final GitHubProvider provider;
    private final GitHubNodeIdResolver gitHubNodeIdResolver;
    private final RepoPermissionService repoPermissionService;
    private final boolean mergeEnabled;

    @Override
    public RepoPermissionService repoPermissionService() {
        return repoPermissionService;
    }

    @Override
    public String providerId() {
        return provider.getProviderId();
    }

    @Override
    public String mergeOperation() {
        return MERGE_OPERATION;
    }

    @Override
    public boolean mergeEnabled() {
        return mergeEnabled;
    }

    @Override
    public GateOutcome evaluate(
            HttpServletRequest request,
            HttpServletResponse response,
            ScmApiRequestContext context,
            RequestBodyWrapper wrapper)
            throws IOException {
        ScmApiGraphQlRequest graphQlRequest;
        Optional<Field> mutation;
        try {
            graphQlRequest = ScmApiGraphQlRequestParser.parse(wrapper.getBody());
            mutation =
                    GraphQlMutationParser.selectMutationField(graphQlRequest.query(), graphQlRequest.operationName());
        } catch (GraphQlParseException e) {
            fail(context, response, HttpServletResponse.SC_BAD_REQUEST, "Malformed GraphQL request: " + e.getMessage());
            return GateOutcome.REFUSED;
        }

        if (mutation.isEmpty()) {
            return GateOutcome.FORWARD;
        }

        Field mutationAst = mutation.get();
        String mutationField = mutationAst.getName();
        context.setMutationField(mutationField);
        context.setVariablesJson(
                graphQlRequest.variables() != null ? graphQlRequest.variables().toString() : null);

        if (!GitHubMutationAllowlist.isAllowed(mutationField)) {
            deny(
                    context,
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Mutation '" + mutationField + "' is not allowlisted");
            return GateOutcome.REFUSED;
        }

        Optional<MutationNodeIdRef> nodeIdRef =
                MutationNodeIdExtractor.extract(mutationAst, graphQlRequest.variables());
        if (nodeIdRef.isEmpty()) {
            fail(
                    context,
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Could not extract a target node ID from mutation '" + mutationField + "'");
            return GateOutcome.REFUSED;
        }
        context.setNodeId(nodeIdRef.get().nodeId());
        context.setNodeType(nodeIdRef.get().nodeType().name());

        String callerToken = ScmApiTokenExtractor.extractToken(request);
        Optional<OwnerRepo> ownerRepo = gitHubNodeIdResolver.resolve(provider, nodeIdRef.get(), callerToken);
        if (ownerRepo.isEmpty()) {
            fail(
                    context,
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Could not resolve node ID '" + nodeIdRef.get().nodeId() + "' to a repository");
            return GateOutcome.REFUSED;
        }
        return new GateOutcome.Mutation(mutationField, ownerRepo.get());
    }
}
