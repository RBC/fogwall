package com.rbc.fogwall.servlet.filter;

import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.GitLabProvider;
import com.rbc.fogwall.scmapi.GitLabProjectIdResolver;
import com.rbc.fogwall.scmapi.GitLabRestAllowlist;
import com.rbc.fogwall.scmapi.GitLabTargetProject;
import com.rbc.fogwall.scmapi.OwnerRepo;
import com.rbc.fogwall.scmapi.ScmApiRestMatch;
import com.rbc.fogwall.servlet.RequestBodyWrapper;
import com.rbc.fogwall.servlet.ScmApiRestPathPolicy;
import com.rbc.fogwall.servlet.ScmApiTokenExtractor;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The GitLab REST dialect of {@link ScmApiRestGateFilter}. GitLab addresses its target directly in the URL as a
 * URL-encoded {@code owner/repo} segment (verified live), so there is no opaque-ID resolution step. Its one deviation
 * from Forgejo is the fork case: {@code mr create} names the upstream only in the body — see {@link #resolveTarget}.
 */
@Slf4j
@RequiredArgsConstructor
public class ScmApiGitLabGateFilter implements ScmApiRestGateFilter {

    static final String MERGE_OPERATION = "merge_requests.merge";

    private final GitLabProvider provider;
    private final GitLabProjectIdResolver projectIdResolver;
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
    public ScmApiRestPathPolicy.EncodedSeparators separators() {
        return ScmApiRestPathPolicy.EncodedSeparators.GITLAB_PROJECT_SEGMENT;
    }

    @Override
    public Optional<ScmApiRestMatch> matchAllowlist(String method, String path) {
        return GitLabRestAllowlist.match(method, path);
    }

    @Override
    public String unresolvedTargetMessage() {
        return "Merge request names a target project that could not be resolved to a repository";
    }

    /**
     * The repository this request must be authorized against: the body's {@code target_project_id} when one is present,
     * otherwise the project named in the URL.
     *
     * <p>{@code glab mr create} posts to the <b>source</b> project and names the upstream only in the body, so the URL
     * alone would authorize the fork — which the contributor owns and can always write to — instead of the upstream the
     * merge request is opened on. Empty means deny: a target that is named but cannot be resolved is exactly when
     * falling back to the URL would check the wrong repository.
     */
    @Override
    public Optional<OwnerRepo> resolveTarget(
            HttpServletRequest request, RequestBodyWrapper wrapper, ScmApiRestMatch match) throws IOException {
        OwnerRepo fromUrl = match.ownerRepo();
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
}
