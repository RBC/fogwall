package com.rbc.fogwall.scmapi;

import com.rbc.fogwall.net.FogwallHttpExecutor;
import com.rbc.fogwall.provider.ForgejoProvider;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.OptionalLong;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.util.Timeout;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resolves a {@code pulls.create} request's {@code head} field to the tip commit SHA it currently names, for
 * {@code providers.<name>.scm-api.require-validated-head} (see {@link HeadCommitValidator}).
 *
 * <p>{@code head} carries the fork only as an {@code owner:branch} prefix, the same shape GitHub uses; the base
 * repository is named in the URL path (see the "Fork PRs address the upstream" section of the SCM API proxy notes). A
 * fork may be renamed away from the base repository's name, so the owner alone does not name a repository.
 *
 * <p>This mirrors Gitea/Forgejo's own pairing of a head owner with a base repository ({@code parseCompareInfo}), which
 * accepts only the head owner's direct fork of the base ({@code GetForkedRepo}), else the base's own parent when the
 * head owner owns it, else the base itself. A sibling fork or a fork of a fork is not a valid head there, so checking
 * only for a parent equal to the base is deliberate. The API has no direct-fork lookup, so it is resolved in this
 * order:
 *
 * <ol>
 *   <li>A fork found earlier for this owner and base, by repository ID from {@link ForkLocationCache}, accepted only if
 *       it is still a fork of the base owned by the head owner.
 *   <li>The base repository's own parent, when the head owner owns it — a PR from upstream into a fork.
 *   <li>When the base has at most {@link #FORK_PAGE_SIZE} forks, its fork list, read in one call — the head owner's
 *       entry. This is exact.
 *   <li>Otherwise the head owner's repository of the base repository's name, only when it is a fork whose parent is the
 *       base.
 *   <li>Otherwise the head owner's forks, most recently updated first, bounded to {@link #MAX_FORK_PAGES} pages — the
 *       one whose parent is the base.
 * </ol>
 *
 * <p>A larger base's fork list is never paged through: a popular upstream has thousands of forks, and this lookup sits
 * inline on the request. A fork found by the last three steps is cached. A head repository none of these finds resolves
 * empty, which the caller refuses.
 */
@Slf4j
public class ForgejoHeadShaResolver {

    /** Pages of the head owner's forks searched before giving up — each page is one inline API call. */
    static final int MAX_FORK_PAGES = 3;

    /**
     * Gitea/Forgejo's default {@code MAX_RESPONSE_ITEMS}. A server configured lower returns shorter pages, so only an
     * empty page ends the search early.
     */
    static final int FORK_PAGE_SIZE = 50;

    private static final JsonMapper MAPPER = new JsonMapper();
    private static final Timeout RESOLVE_TIMEOUT = Timeout.ofSeconds(10);

    private final ForkLocationCache forkLocations;

    public ForgejoHeadShaResolver() {
        this(new ForkLocationCache());
    }

    ForgejoHeadShaResolver(ForkLocationCache forkLocations) {
        this.forkLocations = forkLocations;
    }

    /**
     * Resolves {@code head} to its tip SHA in the head repository, using {@code callerToken} — the caller's own
     * upstream credential, per the BYO-token model. Empty means the head repository or branch could not be resolved and
     * must be treated as "no push record", never skipped.
     */
    public Optional<String> resolveHeadSha(
            ForgejoProvider provider, OwnerRepo baseRepo, String head, String callerToken) {
        int colon = head.indexOf(':');
        String owner = colon < 0 ? baseRepo.owner() : head.substring(0, colon);
        String branch = head.substring(colon + 1);
        try {
            Optional<OwnerRepo> headRepo = owner.equalsIgnoreCase(baseRepo.owner())
                    ? Optional.of(baseRepo)
                    : resolveHeadRepository(provider, baseRepo, owner, callerToken);
            if (headRepo.isEmpty()) {
                log.warn(
                        "No repository owned by '{}' is a fork of {}/{} for provider '{}'",
                        owner,
                        baseRepo.owner(),
                        baseRepo.name(),
                        provider.getProviderId());
                return Optional.empty();
            }
            return get(provider, repoPath(headRepo.get()) + "/branches/" + encode(branch), callerToken)
                    .flatMap(ForgejoHeadShaResolver::extractSha);
        } catch (Exception e) {
            log.warn(
                    "Failed to resolve head '{}' for provider '{}': {}",
                    head,
                    provider.getProviderId(),
                    e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<OwnerRepo> resolveHeadRepository(
            ForgejoProvider provider, OwnerRepo baseRepo, String headOwner, String callerToken) throws Exception {
        String baseFullName = baseRepo.owner() + "/" + baseRepo.name();
        var key = ForkLocationCache.Key.of(provider.getProviderId(), baseRepo, headOwner);

        OptionalLong cachedId = forkLocations.get(key);
        if (cachedId.isPresent()) {
            Optional<JsonNode> cached = get(provider, "/repositories/" + cachedId.getAsLong(), callerToken);
            if (cached.isPresent() && isForkOf(cached.get(), baseFullName) && isOwnedBy(cached.get(), headOwner)) {
                return toOwnerRepo(cached.get());
            }
            forkLocations.invalidate(key, cachedId.getAsLong());
        }

        Optional<JsonNode> base = get(provider, repoPath(baseRepo), callerToken);
        if (base.isEmpty()) {
            return Optional.empty();
        }
        JsonNode parent = base.get().path("parent");
        if (isOwnedBy(parent, headOwner)) {
            return toOwnerRepo(parent);
        }

        Optional<JsonNode> fork = findFork(provider, baseRepo, base.get(), headOwner, callerToken);
        if (fork.isPresent() && fork.get().path("id").isIntegralNumber()) {
            forkLocations.put(key, fork.get().path("id").asLong());
        }
        return fork.flatMap(ForgejoHeadShaResolver::toOwnerRepo);
    }

    /**
     * A base with no more forks than fit one page is answered exactly from its fork list. When that page came back
     * shorter than the base's fork count — a server capping pages below {@link #FORK_PAGE_SIZE}, or forks the caller
     * cannot see — a miss there is not conclusive, so the bounded steps follow.
     */
    private Optional<JsonNode> findFork(
            ForgejoProvider provider, OwnerRepo baseRepo, JsonNode base, String headOwner, String callerToken)
            throws Exception {
        String baseFullName = baseRepo.owner() + "/" + baseRepo.name();

        JsonNode forksCount = base.path("forks_count");
        if (forksCount.isIntegralNumber() && forksCount.asLong() <= FORK_PAGE_SIZE) {
            String query = repoPath(baseRepo) + "/forks?limit=" + FORK_PAGE_SIZE + "&page=1";
            Optional<JsonNode> forks = get(provider, query, callerToken);
            if (forks.isEmpty()) {
                return Optional.empty();
            }
            for (JsonNode fork : forks.get()) {
                if (isOwnedBy(fork, headOwner) && isForkOf(fork, baseFullName)) {
                    return Optional.of(fork);
                }
            }
            if (forks.get().size() >= forksCount.asLong()) {
                return Optional.empty();
            }
        }

        Optional<JsonNode> sameName = get(provider, repoPath(new OwnerRepo(headOwner, baseRepo.name())), callerToken);
        if (sameName.isPresent() && isOwnedBy(sameName.get(), headOwner) && isForkOf(sameName.get(), baseFullName)) {
            return sameName;
        }

        return searchOwnerForks(provider, baseFullName, headOwner, callerToken);
    }

    /**
     * {@code /repos/search} filters by numeric owner ID only, so the owner's ID is looked up first; its
     * {@code mode=fork} keeps the owner's non-fork repositories out of the bounded page budget.
     */
    private Optional<JsonNode> searchOwnerForks(
            ForgejoProvider provider, String baseFullName, String headOwner, String callerToken) throws Exception {
        Optional<JsonNode> user = get(provider, "/users/" + encode(headOwner), callerToken);
        if (user.isEmpty() || !user.get().path("id").canConvertToLong()) {
            return Optional.empty();
        }
        long uid = user.get().path("id").asLong();
        for (int page = 1; page <= MAX_FORK_PAGES; page++) {
            String query = "/repos/search?uid=" + uid + "&exclusive=true&mode=fork&sort=updated&order=desc&limit="
                    + FORK_PAGE_SIZE + "&page=" + page;
            Optional<JsonNode> result = get(provider, query, callerToken);
            if (result.isEmpty() || result.get().path("data").isEmpty()) {
                return Optional.empty();
            }
            for (JsonNode fork : result.get().path("data")) {
                if (isForkOf(fork, baseFullName)) {
                    return Optional.of(fork);
                }
            }
        }
        log.warn(
                "Stopped searching '{}' for a fork of {} after {} pages of {}",
                headOwner,
                baseFullName,
                MAX_FORK_PAGES,
                FORK_PAGE_SIZE);
        return Optional.empty();
    }

    private static boolean isForkOf(JsonNode repository, String baseFullName) {
        JsonNode parent = repository.path("parent").path("full_name");
        return repository.path("fork").asBoolean(false)
                && parent.isString()
                && parent.asString().equalsIgnoreCase(baseFullName);
    }

    private static boolean isOwnedBy(JsonNode repository, String owner) {
        JsonNode login = repository.path("owner").path("login");
        return login.isString() && login.asString().equalsIgnoreCase(owner);
    }

    private static Optional<OwnerRepo> toOwnerRepo(JsonNode repository) {
        JsonNode owner = repository.path("owner").path("login");
        JsonNode name = repository.path("name");
        return owner.isString() && name.isString()
                ? Optional.of(new OwnerRepo(owner.asString(), name.asString()))
                : Optional.empty();
    }

    private static String repoPath(OwnerRepo repo) {
        return "/repos/" + encode(repo.owner()) + "/" + encode(repo.name());
    }

    private static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8);
    }

    /** GETs {@code path} under the API root. Empty on 404; any other failure throws, ending the resolution. */
    private static Optional<JsonNode> get(ForgejoProvider provider, String path, String callerToken) throws Exception {
        var request = Request.get(provider.getApiUrl() + path);
        if (callerToken != null) {
            request.addHeader("Authorization", "token " + callerToken);
        }
        ScmApiUserAgent.self(request);
        try {
            String response = request.connectTimeout(RESOLVE_TIMEOUT)
                    .responseTimeout(RESOLVE_TIMEOUT)
                    .execute(FogwallHttpExecutor.instance())
                    .returnContent()
                    .asString();
            return Optional.of(MAPPER.readTree(response));
        } catch (HttpResponseException e) {
            if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private static Optional<String> extractSha(JsonNode branchResponse) {
        JsonNode id = branchResponse.path("commit").path("id");
        return id.isString() ? Optional.of(id.asString()) : Optional.empty();
    }
}
