package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.ScmApiGateResponse.deny;
import static com.rbc.fogwall.servlet.ScmApiGateResponse.denyWithoutNamingTarget;
import static com.rbc.fogwall.servlet.ScmApiGateResponse.fail;
import static com.rbc.fogwall.servlet.ScmApiGateResponse.tooLarge;
import static com.rbc.fogwall.servlet.ScmApiRequestContext.SCM_API_REQUEST_ATTR;

import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.GitLabProvider;
import com.rbc.fogwall.scmapi.GitLabProjectIdResolver;
import com.rbc.fogwall.scmapi.GitLabRestAllowlist;
import com.rbc.fogwall.scmapi.GitLabTargetProject;
import com.rbc.fogwall.scmapi.OwnerRepo;
import com.rbc.fogwall.scmapi.ScmApiRestMatch;
import com.rbc.fogwall.servlet.PushTooLargeException;
import com.rbc.fogwall.servlet.RequestBodyWrapper;
import com.rbc.fogwall.servlet.ScmApiQueryPolicy;
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
 * The SCM API proxy decision pipeline for GitLab's REST dialect; see {@link ScmApiGitHubGateFilter} for GraphQL. GitLab
 * addresses its target directly in the URL as a URL-encoded {@code owner/repo} path segment (verified from live
 * {@code glab} captures — see the GitLab section of the SCM API proxy notes), so the matched path segment is the
 * authorization target, with no opaque-ID resolution step.
 *
 * <p>Reads (any {@code GET}) are gated by authentication alone — no allowlist, no permission check. Any non-GET request
 * not matching {@link GitLabRestAllowlist} is denied fail-closed. The refusal shapes themselves live in
 * {@link com.rbc.fogwall.servlet.ScmApiGateResponse}, shared with the other two dialects' gate filters.
 */
@Slf4j
@RequiredArgsConstructor
public class ScmApiGitLabGateFilter implements Filter {

    private final GitLabProvider provider;
    private final GitLabProjectIdResolver projectIdResolver;
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
        String method = httpRequest.getMethod();
        String path = ScmApiRestPath.rawSubPath(httpRequest);

        // Checked ahead of the read/mutate split so it covers GETs too, which skip the allowlist.
        if (!ScmApiRestPathPolicy.isForwardable(path, ScmApiRestPathPolicy.EncodedSeparators.GITLAB_PROJECT_SEGMENT)) {
            fail(context, httpResponse, HttpServletResponse.SC_BAD_REQUEST, "Malformed request path");
            return;
        }

        // A request whose shape no supported CLI produces is refused before anything downstream reads what it
        // carries. The forwarder checks again, as it does the path.
        String refusedParameter =
                ScmApiQueryPolicy.refusedParameter(httpRequest.getQueryString(), !"GET".equalsIgnoreCase(method));
        if (refusedParameter != null) {
            deny(
                    context,
                    httpResponse,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Query parameter '" + refusedParameter + "' is not permitted on this request");
            return;
        }

        if ("GET".equalsIgnoreCase(method)) {
            handleRead(httpResponse, chain, wrapper);
            return;
        }

        // The request body is the audit evidence for a write, as the GraphQL variables are for GitHub — recorded
        // before the allowlist so a denied operation carries what it tried to send. The audit filter drops it again
        // on a content rejection, so the secret fogwall just refused never lands in its own database.
        context.setVariablesJson(payloadOf(wrapper));

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
        OwnerRepo sourceRepo = match.get().ownerRepo();

        Optional<OwnerRepo> target = authorizationTarget(httpRequest, wrapper, sourceRepo);
        if (target.isEmpty()) {
            fail(
                    context,
                    httpResponse,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Merge request names a target project that could not be resolved to a repository");
            return;
        }
        OwnerRepo ownerRepo = target.get();
        context.setRepoOwner(ownerRepo.owner());
        context.setRepoName(ownerRepo.name());

        String repoPath = "/" + ownerRepo.owner() + "/" + ownerRepo.name();
        if (!repoPermissionService.isAllowedToPropose(context.getResolvedUser(), provider.getProviderId(), repoPath)) {
            denyWithoutNamingTarget(
                    context,
                    httpResponse,
                    HttpServletResponse.SC_FORBIDDEN,
                    "User '" + context.getResolvedUser() + "' is not permitted to perform API mutations on "
                            + repoPath);
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
        return switch (GitLabTargetProject.targetProjectId(wrapper.getBody())) {
            case GitLabTargetProject.Result.Absent() -> Optional.of(fromUrl);
            case GitLabTargetProject.Result.Unusable(String reason) -> {
                log.warn("Refusing GitLab merge request: {}", reason);
                yield Optional.empty();
            }
            case GitLabTargetProject.Result.Present(String projectId) -> {
                String authHeader = ScmApiTokenExtractor.authHeaderName(request);
                yield projectIdResolver.resolve(
                        provider, projectId, authHeader, authHeader == null ? null : request.getHeader(authHeader));
            }
        };
    }

    private static String payloadOf(RequestBodyWrapper wrapper) {
        byte[] body = wrapper.getBody();
        return body == null || body.length == 0 ? null : new String(body, StandardCharsets.UTF_8);
    }

    private void handleRead(HttpServletResponse response, FilterChain chain, RequestBodyWrapper wrapper)
            throws IOException, ServletException {
        chain.doFilter(wrapper, response);
    }
}
