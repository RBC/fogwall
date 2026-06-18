package com.rbc.fogwall.permission;

import com.rbc.fogwall.db.model.MatchType;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that evaluates whether a proxy user is authorised to push to or approve a push for a given repository.
 *
 * <h3>Fail-closed semantics</h3>
 *
 * <p>If <em>any</em> permission rows exist for the {@code (provider, path)} combination the request is denied unless
 * the user appears in the matching set for the requested operation. If <em>no</em> rows match the path the request is
 * also denied — the permission store must explicitly enumerate every permitted user.
 *
 * <h3>Path matching</h3>
 *
 * <p>Paths use the {@code /owner/repo} convention (leading slash, no {@code .git} suffix). Matching is controlled by
 * {@link MatchType}:
 *
 * <ul>
 *   <li>{@code LITERAL} — exact string equality
 *   <li>{@code GLOB} — {@link java.nio.file.FileSystem#getPathMatcher} with {@code glob:} prefix; {@code *} matches one
 *       path segment, {@code **} matches any depth
 *   <li>{@code REGEX} — full Java regex matched against the full path string
 * </ul>
 */
@Slf4j
public class RepoPermissionService {

    private final RepoPermissionStore store;
    private final ConcurrentHashMap<String, Pattern> patternCache = new ConcurrentHashMap<>();

    public RepoPermissionService(RepoPermissionStore store) {
        this.store = store;
    }

    /**
     * Returns {@code true} when {@code username} is authorised to push to {@code path} at {@code provider}.
     * Fail-closed: returns {@code false} if no grants exist for the path.
     */
    public boolean isAllowedToPush(String username, String provider, String path) {
        return isAllowed(username, provider, path, RepoPermission.Operations.PUSH);
    }

    /**
     * Returns {@code true} when {@code username} is authorised to review (approve or reject) a push for {@code path} at
     * {@code provider}. Fail-closed: returns {@code false} if no grants exist for the path.
     */
    public boolean isAllowedToReview(String username, String provider, String path) {
        return isAllowed(username, provider, path, RepoPermission.Operations.REVIEW);
    }

    /**
     * Returns {@code true} when {@code username} has an explicit {@link RepoPermission.Operations#SELF_CERTIFY} grant
     * for {@code path} at {@code provider}. Unlike push/approve checks,
     * {@link RepoPermission.Operations#PUSH_AND_REVIEW} does <em>not</em> imply self-certify — the grant must be
     * explicit.
     */
    public boolean isBypassReviewAllowed(String username, String provider, String path) {
        List<RepoPermission> forProvider = store.findByProvider(provider);
        List<RepoPermission> forPath =
                forProvider.stream().filter(p -> matchesPath(p, path)).toList();

        if (forPath.isEmpty()) {
            return false;
        }

        boolean allowed = forPath.stream()
                .filter(p -> p.getOperations() == RepoPermission.Operations.SELF_CERTIFY)
                .anyMatch(p -> username.equals(p.getUsername()));

        log.debug(
                "Bypass review check: user={} provider={} path={} → {}",
                username,
                provider,
                path,
                allowed ? "ALLOW" : "DENY");
        return allowed;
    }

    /**
     * Returns the first existing permission that would conflict with {@code incoming}, or empty if none.
     *
     * <p>Two permissions conflict when they share the same username and provider, their paths overlap (exact string
     * equality, or one pattern matches the other path string), AND their operations affect the same permission check.
     * {@link RepoPermission.Operations#SELF_CERTIFY} is evaluated by a separate code path from push/review operations,
     * so a {@code SELF_CERTIFY} entry does not conflict with a {@code PUSH_AND_REVIEW} entry on the same path — both
     * are needed for a trusted committer configuration.
     */
    public Optional<RepoPermission> findConflict(RepoPermission incoming) {
        return store.findAll().stream()
                .filter(e -> !e.getId().equals(incoming.getId()))
                .filter(e -> e.getUsername().equals(incoming.getUsername()))
                .filter(e -> e.getProvider().equals(incoming.getProvider()))
                .filter(e -> pathsOverlap(e, incoming))
                .filter(e -> operationsOverlap(e.getOperations(), incoming.getOperations()))
                .findFirst();
    }

    // ---- store delegation ----

    public void save(RepoPermission permission) {
        store.save(permission);
    }

    public void delete(String id) {
        store.delete(id);
    }

    public Optional<RepoPermission> findById(String id) {
        return store.findById(id);
    }

    public List<RepoPermission> findAll() {
        return store.findAll();
    }

    public List<RepoPermission> findByUsername(String username) {
        return store.findByUsername(username);
    }

    public List<RepoPermission> findByProvider(String provider) {
        return store.findByProvider(provider);
    }

    /**
     * Seeds permissions from config on startup. Clears all CONFIG-sourced rows and re-inserts to keep YAML
     * authoritative; DB-sourced rows are left untouched.
     */
    public void seedFromConfig(List<RepoPermission> permissions) {
        // Remove existing CONFIG rows so YAML changes take effect on restart.
        store.findAll().stream()
                .filter(p -> p.getSource() == RepoPermission.Source.CONFIG)
                .forEach(p -> store.delete(p.getId()));
        for (RepoPermission p : permissions) {
            Optional<RepoPermission> conflict = findConflict(p);
            if (conflict.isPresent()) {
                RepoPermission c = conflict.get();
                throw new IllegalStateException(String.format(
                        "Conflicting permission for user '%s' provider '%s': value '%s' (%s/%s) overlaps with '%s' (%s/%s) [%s] — fix config and restart",
                        p.getUsername(),
                        p.getProvider(),
                        p.getValue(),
                        p.getTarget(),
                        p.getMatchType(),
                        c.getValue(),
                        c.getTarget(),
                        c.getMatchType(),
                        c.getSource()));
            }
            store.save(p);
        }
        log.info("Seeded {} permission grant(s) from config", permissions.size());
    }

    // ---- internals ----

    private boolean operationsOverlap(RepoPermission.Operations a, RepoPermission.Operations b) {
        // SELF_CERTIFY is evaluated by isBypassReviewAllowed(), independent of push/review checks.
        // A SELF_CERTIFY entry only conflicts with another SELF_CERTIFY entry.
        if (a == RepoPermission.Operations.SELF_CERTIFY || b == RepoPermission.Operations.SELF_CERTIFY) {
            return a == b;
        }
        // Among PUSH / REVIEW / PUSH_AND_REVIEW: conflict if both entries would affect the same check.
        // PUSH_AND_REVIEW overlaps with both PUSH and REVIEW; PUSH and REVIEW don't overlap each other.
        boolean aPush = a == RepoPermission.Operations.PUSH || a == RepoPermission.Operations.PUSH_AND_REVIEW;
        boolean bPush = b == RepoPermission.Operations.PUSH || b == RepoPermission.Operations.PUSH_AND_REVIEW;
        boolean aReview = a == RepoPermission.Operations.REVIEW || a == RepoPermission.Operations.PUSH_AND_REVIEW;
        boolean bReview = b == RepoPermission.Operations.REVIEW || b == RepoPermission.Operations.PUSH_AND_REVIEW;
        return (aPush && bPush) || (aReview && bReview);
    }

    private boolean pathsOverlap(RepoPermission a, RepoPermission b) {
        if (a.getValue().equals(b.getValue())) return true;
        if (matchesPath(a, b.getValue())) return true;
        if (matchesPath(b, a.getValue())) return true;
        return false;
    }

    private boolean isAllowed(String username, String provider, String path, RepoPermission.Operations op) {
        List<RepoPermission> forProvider = store.findByProvider(provider);

        List<RepoPermission> forPath =
                forProvider.stream().filter(p -> matchesPath(p, path)).toList();

        if (forPath.isEmpty()) {
            log.debug("No permission grants for {}/{} — DENY (fail-closed)", provider, path);
            return false;
        }

        boolean allowed = forPath.stream()
                .filter(p -> p.getOperations() == op || p.getOperations() == RepoPermission.Operations.PUSH_AND_REVIEW)
                .anyMatch(p -> username.equals(p.getUsername()));

        log.debug(
                "Permission check: user={} provider={} path={} op={} → {}",
                username,
                provider,
                path,
                op,
                allowed ? "ALLOW" : "DENY");
        return allowed;
    }

    private boolean matchesPath(RepoPermission perm, String path) {
        return switch (perm.getMatchType()) {
            case LITERAL -> perm.getValue().equals(path);
            case GLOB -> matchesGlob(perm.getValue(), path);
            case REGEX -> matchesRegex(perm.getValue(), path);
        };
    }

    private boolean matchesGlob(String pattern, String value) {
        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            return matcher.matches(Paths.get(value));
        } catch (Exception e) {
            log.warn("Invalid glob pattern '{}': {}", pattern, e.getMessage());
            return false;
        }
    }

    private boolean matchesRegex(String pattern, String value) {
        try {
            return patternCache
                    .computeIfAbsent(pattern, Pattern::compile)
                    .matcher(value)
                    .matches();
        } catch (PatternSyntaxException e) {
            log.warn("Invalid regex pattern '{}': {}", pattern, e.getMessage());
            return false;
        }
    }
}
