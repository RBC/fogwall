package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.ScmApiGateResponse.deny;
import static com.rbc.fogwall.servlet.ScmApiGateResponse.denyWithoutNamingTarget;
import static com.rbc.fogwall.servlet.ScmApiGateResponse.fail;
import static com.rbc.fogwall.servlet.ScmApiGateResponse.tooLarge;
import static com.rbc.fogwall.servlet.ScmApiRequestContext.SCM_API_REQUEST_ATTR;

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
import com.rbc.fogwall.servlet.PushTooLargeException;
import com.rbc.fogwall.servlet.RequestBodyWrapper;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import com.rbc.fogwall.servlet.ScmApiTokenExtractor;
import graphql.language.Field;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The SCM API proxy decision pipeline for GitHub's GraphQL dialect: parses the request, allowlists the mutation on the
 * parsed AST, resolves its opaque node ID to {@code owner/repo}, and authorizes through the permission engine. Reads
 * (pure {@code query} documents) are gated by authentication alone — no allowlist, no resolution, no extra round-trip.
 * See {@link ScmApiGitLabGateFilter} for the REST dialects.
 *
 * <p>Denies are terminal: this filter responds directly without calling the chain further, so the forward servlet never
 * sees a denied request. The refusal shapes themselves live in {@link com.rbc.fogwall.servlet.ScmApiGateResponse},
 * shared with the other two dialects' gate filters — only how a request gets parsed into one of those decision points
 * differs per dialect.
 */
@Slf4j
@RequiredArgsConstructor
public class ScmApiGitHubGateFilter implements Filter {

    private final GitHubProvider provider;
    private final GitHubNodeIdResolver gitHubNodeIdResolver;
    private final RepoPermissionService repoPermissionService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        var context = (ScmApiRequestContext) httpRequest.getAttribute(SCM_API_REQUEST_ATTR);

        // Cheap pre-check, then a counting read: a chunked request declares no length, so the wrapper is the bound.
        long declared = httpRequest.getContentLengthLong();
        if (declared > ScmApiRequestContext.MAX_BODY_BYTES) {
            tooLarge(context, httpResponse, declared);
            return;
        }
        RequestBodyWrapper wrapper;
        try {
            wrapper = new RequestBodyWrapper(httpRequest, ScmApiRequestContext.MAX_BODY_BYTES);
        } catch (PushTooLargeException e) {
            tooLarge(context, httpResponse, e.getBytesRead());
            return;
        }

        ScmApiGraphQlRequest graphQlRequest;
        Optional<Field> mutation;
        try {
            graphQlRequest = ScmApiGraphQlRequestParser.parse(wrapper.getBody());
            mutation =
                    GraphQlMutationParser.selectMutationField(graphQlRequest.query(), graphQlRequest.operationName());
        } catch (GraphQlParseException e) {
            fail(
                    context,
                    httpResponse,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Malformed GraphQL request: " + e.getMessage());
            return;
        }

        if (mutation.isEmpty()) {
            handleRead(httpResponse, chain, wrapper);
            return;
        }

        Field mutationAst = mutation.get();
        String mutationField = mutationAst.getName();
        context.setMutationField(mutationField);
        context.setVariablesJson(
                graphQlRequest.variables() != null ? graphQlRequest.variables().toString() : null);

        if (!GitHubMutationAllowlist.isAllowed(mutationField)) {
            deny(
                    context,
                    httpResponse,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Mutation '" + mutationField + "' is not allowlisted");
            return;
        }

        Optional<MutationNodeIdRef> nodeIdRef =
                MutationNodeIdExtractor.extract(mutationAst, graphQlRequest.variables());
        if (nodeIdRef.isEmpty()) {
            fail(
                    context,
                    httpResponse,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Could not extract a target node ID from mutation '" + mutationField + "'");
            return;
        }
        context.setNodeId(nodeIdRef.get().nodeId());
        context.setNodeType(nodeIdRef.get().nodeType().name());

        String callerToken = ScmApiTokenExtractor.extractToken(httpRequest);
        Optional<OwnerRepo> ownerRepo = gitHubNodeIdResolver.resolve(provider, nodeIdRef.get(), callerToken);
        if (ownerRepo.isEmpty()) {
            fail(
                    context,
                    httpResponse,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Could not resolve node ID '" + nodeIdRef.get().nodeId() + "' to a repository");
            return;
        }
        context.setRepoOwner(ownerRepo.get().owner());
        context.setRepoName(ownerRepo.get().name());

        String path = "/" + ownerRepo.get().owner() + "/" + ownerRepo.get().name();
        if (!repoPermissionService.isAllowedToPropose(context.getResolvedUser(), provider.getProviderId(), path)) {
            denyWithoutNamingTarget(
                    context,
                    httpResponse,
                    HttpServletResponse.SC_FORBIDDEN,
                    "User '" + context.getResolvedUser() + "' is not permitted to perform API mutations on " + path);
            return;
        }

        chain.doFilter(wrapper, response);
    }

    private void handleRead(HttpServletResponse response, FilterChain chain, RequestBodyWrapper wrapper)
            throws IOException, ServletException {
        chain.doFilter(wrapper, response);
    }
}
