package com.rbc.fogwall.servlet.filter;

import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.ForgejoProvider;
import com.rbc.fogwall.scmapi.ForgejoRestAllowlist;
import com.rbc.fogwall.scmapi.OwnerRepo;
import com.rbc.fogwall.scmapi.ScmApiRestMatch;
import com.rbc.fogwall.servlet.RequestBodyWrapper;
import com.rbc.fogwall.servlet.ScmApiRestPathPolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/**
 * The Forgejo/Gitea REST dialect of {@link ScmApiRestGateFilter}. One filter serves both {@code tea} and {@code fj},
 * whose endpoints {@link ForgejoRestAllowlist} holds as a union. Owner and repo are two plain path segments, so the
 * matched path is the authorization target directly — no fork-body resolution as GitLab needs.
 */
@RequiredArgsConstructor
public class ScmApiForgejoGateFilter implements ScmApiRestGateFilter {

    static final String MERGE_OPERATION = "pulls.merge";

    private final ForgejoProvider provider;
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
        return ScmApiRestPathPolicy.EncodedSeparators.FORGEJO_FILE_PATH;
    }

    @Override
    public Optional<ScmApiRestMatch> matchAllowlist(String method, String path) {
        return ForgejoRestAllowlist.match(method, path);
    }

    @Override
    public Optional<OwnerRepo> resolveTarget(
            HttpServletRequest request, RequestBodyWrapper wrapper, ScmApiRestMatch match) {
        return Optional.of(match.ownerRepo());
    }
}
