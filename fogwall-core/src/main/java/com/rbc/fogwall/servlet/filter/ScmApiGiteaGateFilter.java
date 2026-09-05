package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.ScmApiRequestContext.SCM_API_REQUEST_ATTR;

import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.ForgejoProvider;
import com.rbc.fogwall.scmapi.GiteaRestAllowlist;
import com.rbc.fogwall.scmapi.OwnerRepo;
import com.rbc.fogwall.scmapi.ScmApiAccessEvaluator;
import com.rbc.fogwall.scmapi.ScmApiAccessRule;
import com.rbc.fogwall.scmapi.ScmApiRestMatch;
import com.rbc.fogwall.servlet.RequestBodyWrapper;
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
 * The SCM API proxy decision pipeline for the Gitea/Forgejo REST dialect. Structurally identical to
 * {@link ScmApiGitLabGateFilter} — both are path-addressed REST, so neither needs GitHub's opaque-ID resolution — and
 * differs only in which allowlist table it consults.
 *
 * <p>A single filter serves both {@code tea} and {@code fj}: they speak the same server API, so
 * {@link GiteaRestAllowlist} is the union of the endpoints each exercises. See that class for why the two CLIs are
 * deliberately not distinguished by {@code User-Agent}.
 *
 * <p>Reads (any {@code GET}) are gated only by the coarser, provider-level {@link ScmApiAccessEvaluator} — no
 * allowlist, no permission check — keeping the default read cost near pass-through, same policy as the other dialects.
 * Any non-GET request that doesn't match the allowlist is denied fail-closed.
 */
@Slf4j
@RequiredArgsConstructor
public class ScmApiGiteaGateFilter implements Filter {

    private final ForgejoProvider provider;
    private final ScmApiAccessEvaluator accessEvaluator;
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

        // Gitea/Forgejo address owner and repo as separate plain segments, so an encoded separator is never
        // legitimate here — refused outright, unlike GitLab's project segment. Checked ahead of the read/mutate
        // split so GETs, which skip the allowlist, are covered too.
        if (!ScmApiRestPathPolicy.isForwardable(path, ScmApiRestPathPolicy.EncodedSeparators.REJECTED)) {
            deny(context, httpResponse, HttpServletResponse.SC_BAD_REQUEST, "Malformed request path");
            return;
        }

        if ("GET".equalsIgnoreCase(method)) {
            handleRead(httpResponse, chain, wrapper);
            return;
        }

        Optional<ScmApiRestMatch> match = GiteaRestAllowlist.match(method, path);
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

        var mutateAccess = accessEvaluator.evaluate(provider.getProviderId(), ScmApiAccessRule.Operation.MUTATE);
        if (!(mutateAccess instanceof ScmApiAccessEvaluator.Result.Allowed)) {
            deny(
                    context,
                    httpResponse,
                    HttpServletResponse.SC_FORBIDDEN,
                    "SCM API mutations are not enabled for this provider");
            return;
        }

        String repoPath = "/" + ownerRepo.owner() + "/" + ownerRepo.name();
        if (!repoPermissionService.isAllowedToPropose(context.getResolvedUser(), provider.getProviderId(), repoPath)) {
            deny(
                    context,
                    httpResponse,
                    HttpServletResponse.SC_FORBIDDEN,
                    "User '" + context.getResolvedUser() + "' is not permitted to perform API mutations on "
                            + repoPath);
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
