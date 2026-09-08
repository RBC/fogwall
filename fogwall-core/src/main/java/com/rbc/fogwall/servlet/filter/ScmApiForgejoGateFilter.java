package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.ScmApiGateResponse.deny;
import static com.rbc.fogwall.servlet.ScmApiGateResponse.denyWithoutNamingTarget;
import static com.rbc.fogwall.servlet.ScmApiGateResponse.fail;
import static com.rbc.fogwall.servlet.ScmApiGateResponse.tooLarge;
import static com.rbc.fogwall.servlet.ScmApiRequestContext.SCM_API_REQUEST_ATTR;

import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.ForgejoProvider;
import com.rbc.fogwall.scmapi.ForgejoRestAllowlist;
import com.rbc.fogwall.scmapi.OwnerRepo;
import com.rbc.fogwall.scmapi.ScmApiRestMatch;
import com.rbc.fogwall.servlet.PushTooLargeException;
import com.rbc.fogwall.servlet.RequestBodyWrapper;
import com.rbc.fogwall.servlet.ScmApiQueryPolicy;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import com.rbc.fogwall.servlet.ScmApiRestPath;
import com.rbc.fogwall.servlet.ScmApiRestPathPolicy;
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
 * The SCM API proxy decision pipeline for the Forgejo REST dialect; see {@link ScmApiGitLabGateFilter}. One filter
 * serves both {@code tea} and {@code fj}, whose endpoints {@link ForgejoRestAllowlist} holds as a union.
 *
 * <p>Reads (any {@code GET}) are gated by authentication alone — no allowlist, no permission check. Any non-GET request
 * not matching the allowlist is denied fail-closed. The refusal shapes themselves live in
 * {@link com.rbc.fogwall.servlet.ScmApiGateResponse}, shared with the other two dialects' gate filters.
 */
@Slf4j
@RequiredArgsConstructor
public class ScmApiForgejoGateFilter implements Filter {

    private final ForgejoProvider provider;
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

        // Forgejo name owner and repo as separate plain segments, but encode a repository-relative file path
        // into one segment of its blob endpoints — fj reads a pull request template from there before creating one.
        // Checked ahead of the read/mutate split so that GET, which skips the allowlist, is covered too.
        if (!ScmApiRestPathPolicy.isForwardable(path, ScmApiRestPathPolicy.EncodedSeparators.FORGEJO_FILE_PATH)) {
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

        Optional<ScmApiRestMatch> match = ForgejoRestAllowlist.match(method, path);
        if (match.isEmpty()) {
            deny(
                    context,
                    httpResponse,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Operation '" + method + " " + path + "' is not allowlisted");
            return;
        }

        String operation = match.get().operation();
        OwnerRepo ownerRepo = match.get().ownerRepo();
        context.setMutationField(operation);
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

    private static String payloadOf(RequestBodyWrapper wrapper) {
        byte[] body = wrapper.getBody();
        return body == null || body.length == 0 ? null : new String(body, StandardCharsets.UTF_8);
    }

    private void handleRead(HttpServletResponse response, FilterChain chain, RequestBodyWrapper wrapper)
            throws IOException, ServletException {
        chain.doFilter(wrapper, response);
    }
}
