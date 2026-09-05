package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.ScmApiRequestContext.SCM_API_REQUEST_ATTR;

import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.scmapi.GitHubMutationAllowlist;
import com.rbc.fogwall.scmapi.GraphQlMutationParser;
import com.rbc.fogwall.scmapi.GraphQlParseException;
import com.rbc.fogwall.scmapi.MutationNodeIdExtractor;
import com.rbc.fogwall.scmapi.MutationNodeIdRef;
import com.rbc.fogwall.scmapi.NodeIdResolver;
import com.rbc.fogwall.scmapi.OwnerRepo;
import com.rbc.fogwall.scmapi.ScmApiAccessEvaluator;
import com.rbc.fogwall.scmapi.ScmApiAccessRule;
import com.rbc.fogwall.scmapi.ScmApiGraphQlRequest;
import com.rbc.fogwall.scmapi.ScmApiGraphQlRequestParser;
import com.rbc.fogwall.servlet.RequestBodyWrapper;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import com.rbc.fogwall.servlet.ScmApiTokenExtractor;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The SCM API proxy decision pipeline for GitHub's GraphQL dialect: parses the GraphQL request, allowlists any mutation
 * on the parsed AST, resolves its opaque node ID to {@code owner/repo}, and authorizes via the existing permission
 * engine. Reads (pure {@code query} documents) are gated only by the coarser, provider-level
 * {@link ScmApiAccessEvaluator} — no allowlist, no resolution, no extra round-trip, keeping the default read cost near
 * pass-through. GitLab's REST dialect has its own parallel pipeline in {@link ScmApiGitLabGateFilter}, since the two
 * wire formats share almost nothing below the authorization step.
 *
 * <p>Denies are terminal here: this filter sets {@link ScmApiRequestContext#setStatus} and responds directly, without
 * calling the chain further, so {@link ScmApiGraphQlForwardServlet} never sees a denied request. Only an allowed
 * mutation or an allowed read continues down the chain.
 */
@Slf4j
@RequiredArgsConstructor
public class ScmApiGitHubGateFilter implements Filter {

    private final GitHubProvider provider;
    private final ScmApiAccessEvaluator accessEvaluator;
    private final NodeIdResolver nodeIdResolver;
    private final RepoPermissionService repoPermissionService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        var context = (ScmApiRequestContext) httpRequest.getAttribute(SCM_API_REQUEST_ATTR);

        RequestBodyWrapper wrapper = new RequestBodyWrapper(httpRequest);

        ScmApiGraphQlRequest graphQlRequest;
        List<String> mutationFields;
        try {
            graphQlRequest = ScmApiGraphQlRequestParser.parse(wrapper.getBody());
            mutationFields = GraphQlMutationParser.extractMutationFields(graphQlRequest.query());
        } catch (GraphQlParseException e) {
            deny(
                    context,
                    httpResponse,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Malformed GraphQL request: " + e.getMessage());
            return;
        }

        if (mutationFields.isEmpty()) {
            handleRead(httpResponse, chain, wrapper);
            return;
        }

        // Verified gh traffic never batches more than one mutation per request (docs/internals/SCM_API_PROXY.md);
        // handle the general case defensively but only the first mutation drives the decision.
        String mutationField = mutationFields.get(0);
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

        var mutateAccess = accessEvaluator.evaluate(provider.getProviderId(), ScmApiAccessRule.Operation.MUTATE);
        if (!(mutateAccess instanceof ScmApiAccessEvaluator.Result.Allowed)) {
            deny(
                    context,
                    httpResponse,
                    HttpServletResponse.SC_FORBIDDEN,
                    "SCM API mutations are not enabled for this provider");
            return;
        }

        Optional<MutationNodeIdRef> nodeIdRef =
                MutationNodeIdExtractor.extract(mutationField, graphQlRequest.variables());
        if (nodeIdRef.isEmpty()) {
            deny(
                    context,
                    httpResponse,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Could not extract a target node ID from mutation '" + mutationField + "'");
            return;
        }
        context.setNodeId(nodeIdRef.get().nodeId());
        context.setNodeType(nodeIdRef.get().nodeType().name());

        String callerToken = ScmApiTokenExtractor.extractToken(httpRequest);
        Optional<OwnerRepo> ownerRepo = nodeIdResolver.resolve(provider, nodeIdRef.get(), callerToken);
        if (ownerRepo.isEmpty()) {
            deny(
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
            deny(
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
        var readAccess = accessEvaluator.evaluate(provider.getProviderId(), ScmApiAccessRule.Operation.READ);
        if (!(readAccess instanceof ScmApiAccessEvaluator.Result.Allowed)) {
            log.debug("SCM API read denied for provider '{}': no matching allow rule", provider.getProviderId());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getOutputStream()
                    .write("{\"error\":\"reads are not enabled for this provider\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(wrapper, response);
    }

    private static void deny(ScmApiRequestContext context, HttpServletResponse response, int status, String reason)
            throws IOException {
        log.debug("SCM API proxy request denied: {}", reason);
        if (context != null) {
            context.setStatus(ScmApiActionStatus.DENIED);
            context.setReason(reason);
        }
        response.setStatus(status);
        response.setContentType("application/json");
        response.getOutputStream()
                .write(("{\"error\":\"" + reason.replace("\"", "'") + "\"}").getBytes(StandardCharsets.UTF_8));
    }
}
