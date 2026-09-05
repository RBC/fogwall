package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.ScmApiRequestContext.SCM_API_REQUEST_ATTR;

import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.GitLabProvider;
import com.rbc.fogwall.scmapi.GitLabProjectIdResolver;
import com.rbc.fogwall.scmapi.GitLabRestAllowlist;
import com.rbc.fogwall.scmapi.GitLabTargetProject;
import com.rbc.fogwall.scmapi.OwnerRepo;
import com.rbc.fogwall.scmapi.ScmApiAccessEvaluator;
import com.rbc.fogwall.scmapi.ScmApiAccessRule;
import com.rbc.fogwall.scmapi.ScmApiRestMatch;
import com.rbc.fogwall.servlet.RequestBodyWrapper;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import com.rbc.fogwall.servlet.ScmApiRestPath;
import com.rbc.fogwall.servlet.ScmApiRestPathPolicy;
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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The SCM API proxy decision pipeline for GitLab's REST dialect — parallel to {@link ScmApiGitHubGateFilter}'s GraphQL
 * pipeline, not a variant of it. GitLab addresses its target directly in the URL as a URL-encoded {@code owner/repo}
 * path segment (verified from live {@code glab} captures — see docs/internals/SCM_API_PROXY.md's GitLab section), so
 * unlike GitHub there is no opaque-ID resolution step: the matched path segment IS the authorization target.
 *
 * <p>Reads (any {@code GET}) are gated only by the coarser, provider-level {@link ScmApiAccessEvaluator} — no
 * allowlist, no permission check — keeping the default read cost near pass-through, same policy as the GitHub dialect.
 * Any non-GET request that doesn't match {@link GitLabRestAllowlist} is denied fail-closed.
 */
@Slf4j
@RequiredArgsConstructor
public class ScmApiGitLabGateFilter implements Filter {

    private final GitLabProvider provider;
    private final ScmApiAccessEvaluator accessEvaluator;
    private final GitLabProjectIdResolver projectIdResolver;
    private final RepoPermissionService repoPermissionService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        var context = (ScmApiRequestContext) httpRequest.getAttribute(SCM_API_REQUEST_ATTR);

        RequestBodyWrapper wrapper = new RequestBodyWrapper(httpRequest);
        String method = httpRequest.getMethod();
        String path = ScmApiRestPath.rawSubPath(httpRequest);

        // Checked ahead of the read/mutate split so it covers GETs too: reads skip the allowlist, which would
        // otherwise leave the forwarded path unexamined on exactly the requests fogwall inspects least.
        if (!ScmApiRestPathPolicy.isForwardable(path, ScmApiRestPathPolicy.EncodedSeparators.GITLAB_PROJECT_SEGMENT)) {
            deny(context, httpResponse, HttpServletResponse.SC_BAD_REQUEST, "Malformed request path");
            return;
        }

        if ("GET".equalsIgnoreCase(method)) {
            handleRead(httpResponse, chain, wrapper);
            return;
        }

        Optional<ScmApiRestMatch> match = GitLabRestAllowlist.match(method, path);
        if (match.isEmpty()) {
            deny(
                    context,
                    httpResponse,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Operation '" + method + " " + path + "' is not allowlisted");
            return;
        }

        String operation = match.get().operation();
        context.setMutationField(operation);

        Optional<OwnerRepo> target =
                authorizationTarget(httpRequest, wrapper, match.get().ownerRepo());
        if (target.isEmpty()) {
            deny(
                    context,
                    httpResponse,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Merge request names a target project that could not be resolved to a repository");
            return;
        }
        OwnerRepo ownerRepo = target.get();
        context.setRepoOwner(ownerRepo.owner());
        context.setRepoName(ownerRepo.name());

        var mutateAccess = accessEvaluator.evaluate(provider.getProviderId(), ScmApiAccessRule.Operation.MUTATE);
        if (!(mutateAccess instanceof ScmApiAccessEvaluator.Result.Allowed)) {
            deny(
                    context,
                    httpResponse,
                    HttpServletResponse.SC_FORBIDDEN,
                    "SCM API mutations are not enabled for this provider");
            return;
        }

        String path2 = "/" + ownerRepo.owner() + "/" + ownerRepo.name();
        if (!repoPermissionService.isAllowedToPropose(context.getResolvedUser(), provider.getProviderId(), path2)) {
            deny(
                    context,
                    httpResponse,
                    HttpServletResponse.SC_FORBIDDEN,
                    "User '" + context.getResolvedUser() + "' is not permitted to perform API mutations on " + path2);
            return;
        }

        chain.doFilter(wrapper, response);
    }

    /**
     * The repository this request must be authorized against: the body's {@code target_project_id} when one is present,
     * otherwise the project named in the URL.
     *
     * <p>This is the fork case. {@code glab mr create} posts to the <b>source</b> project and names the upstream only
     * in the body, so the URL alone would authorize the fork — which the contributor owns and can always write to —
     * instead of the upstream the merge request is opened on. Empty means deny: a target that is named but cannot be
     * resolved is exactly when falling back to the URL would check the wrong repository.
     */
    private Optional<OwnerRepo> authorizationTarget(
            HttpServletRequest request, RequestBodyWrapper wrapper, OwnerRepo fromUrl) throws IOException {
        GitLabTargetProject.Result result = GitLabTargetProject.targetProjectId(wrapper.getBody());
        if (result instanceof GitLabTargetProject.Result.Absent) {
            return Optional.of(fromUrl);
        }
        if (result instanceof GitLabTargetProject.Result.Unusable unusable) {
            log.warn("Refusing GitLab merge request: {}", unusable.reason());
            return Optional.empty();
        }

        String projectId = ((GitLabTargetProject.Result.Present) result).projectId();
        String authHeader = ScmApiTokenExtractor.authHeaderName(request);
        return projectIdResolver.resolve(
                provider, projectId, authHeader, authHeader == null ? null : request.getHeader(authHeader));
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
