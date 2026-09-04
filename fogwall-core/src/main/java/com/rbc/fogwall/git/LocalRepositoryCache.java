package com.rbc.fogwall.git;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

/**
 * Manages local clones of remote repositories for inspection and filtering. This service uses JGit to clone
 * repositories into temporary directories and maintains a cache of these clones.
 *
 * <h2>Concurrency</h2>
 *
 * <p><b>First clones</b> are serialized per repository by a {@link ReentrantLock} keyed on the cache key
 * ({@code cloneLocks}). Two threads racing on the first access to the same URL cannot both launch a {@code git clone}
 * into the same mirror directory (which would interleave and fail): the winner clones, the loser double-checks the
 * cache and is handed the same mirror. First clones of <em>different</em> repositories run concurrently — the lock is
 * per key, not instance-wide.
 *
 * <p><b>Upstream fetches</b> for one repository are serialized by that repository's own {@link ReentrantLock}
 * ({@code CachedRepository.fetchLock}), so two {@code git fetch} operations never write the same bare repo at once. A
 * {@code fetchCooldownMs} guard additionally skips a re-fetch when the mirror was refreshed recently, avoiding
 * redundant network round-trips.
 *
 * <h3>Serve vs. fetch: intentionally lock-free</h3>
 *
 * <p>A refresh fetch (writer) can run while an {@code UploadPack}/{@code ReceivePack} or content inspection (reader) is
 * using the same mirror. This is a <b>deliberate design decision, not an oversight</b>: readers are <em>not</em>
 * blocked against the refreshing writer, keeping the serve path — fogwall's hot path — free of lock contention.
 *
 * <p>The trade is sound because a {@code git fetch} is <b>additive</b>: it writes new pack files and advances refs but
 * never deletes the objects a concurrent reader is already serving (object removal only happens under {@code gc}/
 * {@code prune}, which a plain fetch does not trigger). So the usual consequence of an overlap is merely that the
 * reader serves a slightly <em>stale</em> snapshot — it doesn't yet see commits the refresh just added — and the client
 * re-fetches later to pick them up. On a fogwall gateway in server mode this brief, self-healing staleness (bounded by
 * the fetch cooldown) is acceptable.
 *
 * <p>A per-repository read/write lock was considered and rejected: to be safe it would either make refreshes wait for
 * readers to drain — which under sustained automated fetch traffic (e.g. CI polling) can <em>starve the writer</em> so
 * the mirror grows staler, the opposite of the intent — or make readers wait for refreshes, taxing the hot path. Paying
 * either cost to prevent a consequence that is normally benign staleness is a poor trade.
 *
 * <p>The one non-benign residual is on <b>shallow</b> mirrors, where a reachability race near the shallow boundary can
 * make a concurrent read fail with {@code want <sha> not valid}. That failure is a transient, retryable request error —
 * not corruption, not data loss, not an authorization bypass — and the client simply retries. A deployment that serves
 * high-churn shallow mirrors and wants to eliminate even that transient can set {@code cloneDepth=0} on the serve cache
 * to remove the shallow boundary entirely.
 */
@Slf4j
public class LocalRepositoryCache {

    private static final int DEFAULT_CLONE_DEPTH = 100;

    /**
     * Default minimum interval between upstream re-fetches for the same repository. Prevents concurrent serve requests
     * from each triggering a separate fetch when the mirror is already fresh.
     */
    private static final long DEFAULT_FETCH_COOLDOWN_MS = 5_000;

    /**
     * Identity used when a caller supplies no principal. Not a hash, so it can never collide with a real hashed
     * principal — an unauthenticated caller only ever shares a cooldown with other unauthenticated callers.
     */
    private static final String ANONYMOUS_PRINCIPAL = "anonymous";

    private final Path cacheDirectory;
    private final Map<String, CachedRepository> cache = new ConcurrentHashMap<>();

    /**
     * Per-cache-key locks that serialize the <em>first</em> clone/fetch of a repository without serializing across
     * <em>different</em> repositories. Before, {@code cloneOrFetch} was {@code synchronized} on the whole cache, so a
     * slow first clone of one repo (seconds over the network) blocked the first clone of every other repo behind the
     * same monitor. Keying the lock on the cache key means two threads racing on the same new repo still dedupe (the
     * loser double-checks the cache and returns the winner's clone), while first clones of distinct repos proceed in
     * parallel. Entries are never removed: one {@link ReentrantLock} per distinct repository ever seen is a negligible,
     * bounded footprint, and removal would reintroduce a race with a thread about to acquire the same key's lock.
     */
    private final Map<String, ReentrantLock> cloneLocks = new ConcurrentHashMap<>();

    private final int cloneDepth;

    /**
     * Time-based shallow boundary (#476). When non-null it takes precedence over {@link #cloneDepth}: clones and first
     * fetches keep history back to {@code now - shallowSince} rather than a fixed commit count. The boundary is
     * computed fresh at each clone so it tracks wall-clock time over a long-running process rather than freezing at
     * startup.
     */
    private final Duration shallowSince;

    private final boolean registerShutdownHook;
    private final long fetchCooldownMs;

    /** Default constructor that uses system temp directory with shutdown hook. */
    public LocalRepositoryCache() throws IOException {
        this(Files.createTempDirectory("fogwall-cache-"), DEFAULT_CLONE_DEPTH, true);
    }

    /**
     * Constructor with custom cache directory - Spring-friendly (no shutdown hook).
     *
     * @param cacheDirectory The directory to use for caching repositories
     * @param registerShutdownHook Whether to register shutdown hook (false for Spring apps)
     */
    public LocalRepositoryCache(Path cacheDirectory, boolean registerShutdownHook) throws IOException {
        this(cacheDirectory, DEFAULT_CLONE_DEPTH, registerShutdownHook);
    }

    /**
     * Full constructor with custom cache directory and clone depth.
     *
     * @param cacheDirectory The directory to use for caching repositories
     * @param cloneDepth The depth for shallow clones (0 for full clone)
     * @param registerShutdownHook Whether to register shutdown hook (false for Spring apps)
     */
    public LocalRepositoryCache(Path cacheDirectory, int cloneDepth, boolean registerShutdownHook) {
        this(cacheDirectory, cloneDepth, registerShutdownHook, DEFAULT_FETCH_COOLDOWN_MS);
    }

    /**
     * Constructor with a time-based shallow boundary (#476). When {@code shallowSince} is non-null it takes precedence
     * over {@code cloneDepth}. Uses the default fetch cooldown.
     *
     * @param cacheDirectory The directory to use for caching repositories
     * @param cloneDepth The depth for shallow clones (0 for full clone); ignored when {@code shallowSince} is set
     * @param registerShutdownHook Whether to register shutdown hook (false for Spring apps)
     * @param shallowSince Keep history back to {@code now - shallowSince}, or {@code null} to use {@code cloneDepth}
     */
    public LocalRepositoryCache(
            Path cacheDirectory, int cloneDepth, boolean registerShutdownHook, Duration shallowSince) {
        this(cacheDirectory, cloneDepth, registerShutdownHook, DEFAULT_FETCH_COOLDOWN_MS, shallowSince);
    }

    /**
     * Full constructor with cooldown but no time-based boundary. Retained for callers that pass a custom cooldown.
     *
     * @param cacheDirectory The directory to use for caching repositories
     * @param cloneDepth The depth for shallow clones (0 for full clone)
     * @param registerShutdownHook Whether to register shutdown hook (false for Spring apps)
     * @param fetchCooldownMs Minimum milliseconds between upstream re-fetches for the same repository
     */
    public LocalRepositoryCache(
            Path cacheDirectory, int cloneDepth, boolean registerShutdownHook, long fetchCooldownMs) {
        this(cacheDirectory, cloneDepth, registerShutdownHook, fetchCooldownMs, null);
    }

    /**
     * Full constructor with all options.
     *
     * @param cacheDirectory The directory to use for caching repositories
     * @param cloneDepth The depth for shallow clones (0 for full clone); ignored when {@code shallowSince} is set
     * @param registerShutdownHook Whether to register shutdown hook (false for Spring apps)
     * @param fetchCooldownMs Minimum milliseconds between upstream re-fetches for the same repository
     * @param shallowSince Keep history back to {@code now - shallowSince}, or {@code null} to use {@code cloneDepth}
     */
    public LocalRepositoryCache(
            Path cacheDirectory,
            int cloneDepth,
            boolean registerShutdownHook,
            long fetchCooldownMs,
            Duration shallowSince) {
        this.cacheDirectory = cacheDirectory;
        this.cloneDepth = cloneDepth;
        this.shallowSince = shallowSince;
        this.registerShutdownHook = registerShutdownHook;
        this.fetchCooldownMs = fetchCooldownMs;
        if (shallowSince != null) {
            log.info("Initialized LocalRepositoryCache at: {} with shallow-since: {}", cacheDirectory, shallowSince);
        } else {
            log.info("Initialized LocalRepositoryCache at: {} with clone depth: {}", cacheDirectory, cloneDepth);
        }
        if (registerShutdownHook) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));
        }
    }

    /** True when this cache clones shallow — by a time boundary or a positive commit depth. */
    private boolean isShallow() {
        return shallowSince != null || cloneDepth > 0;
    }

    /** Human-readable description of the shallow strategy for log lines. */
    private String describeShallow() {
        if (shallowSince != null) return "shallow-since " + shallowSince;
        return cloneDepth > 0 ? "depth " + cloneDepth : "full history";
    }

    /**
     * Get or create a local clone of a remote repository <b>as an anonymous caller</b> — no credentials, no principal.
     *
     * <p>Only appropriate when the repository is expected to be publicly readable. All anonymous callers share a single
     * cache identity, which is safe precisely because an anonymous cache hit can only ever reuse a previous anonymous
     * fetch, and that fetch succeeded without credentials. If you have credentials, you also have an identity: use
     * {@link #getOrClone(String, CredentialsProvider, TransportConfigCallback, String)} and pass both. There is
     * deliberately no overload that accepts credentials without a principal — supplying one and not the other means
     * authenticating the fetch while leaving the cooldown anonymous, which silently makes one caller's access reusable
     * by the next.
     *
     * @param remoteUrl The URL of the remote repository
     * @return The local repository
     * @throws GitAPIException If git operations fail
     * @throws IOException If I/O operations fail
     */
    public Repository getOrClone(String remoteUrl) throws GitAPIException, IOException {
        return getOrClone(remoteUrl, null, null, null);
    }

    /**
     * Get or create a local clone, recording which principal's credentials last successfully reached upstream.
     *
     * <p><b>Why the principal matters.</b> The fetch cooldown exists to avoid redundant network round-trips, but the
     * upstream fetch it skips is also the only thing that proves the caller may access the repository — there is no
     * separate read-authorization check on this path. Keyed on the repository alone, the cooldown therefore made one
     * principal's authorization transferable to any other principal who asked within the window: the mirror would be
     * handed back without their credentials ever being sent anywhere. Keying it on {@code (repository, principal)}
     * means an unrecognised principal always forces a real upstream fetch with their own credentials, so an
     * unauthorized caller fails closed instead of inheriting someone else's access.
     *
     * @param principal opaque identity of the caller — HTTP Basic {@code user:token}, an SSH key fingerprint, or
     *     {@code null} for an unauthenticated caller (all of which share a single anonymous identity). Hashed before
     *     use; the raw value is never retained.
     */
    public Repository getOrClone(
            String remoteUrl,
            CredentialsProvider credentials,
            TransportConfigCallback transportConfig,
            String principal)
            throws GitAPIException, IOException {
        String cacheKey = getCacheKey(remoteUrl);
        String principalKey = hashPrincipal(principal);

        CachedRepository cached = cache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            log.debug("Using cached repository for: {}", remoteUrl);
            refreshIfStale(cached, cacheKey, credentials, transportConfig, principalKey);
            cached.repository.incrementOpen();
            return cached.repository;
        }

        log.info("Cloning repository from: {}", remoteUrl);
        return cloneOrFetch(remoteUrl, cacheKey, credentials, transportConfig, principalKey);
    }

    /**
     * Fetches from upstream immediately, ignoring the cooldown, and deepens a shallow mirror to full history.
     *
     * <p>For callers that are about to make a decision a stale or truncated mirror would get <b>wrong</b>, rather than
     * merely slightly out of date. The cooldown optimises for content freshness and the clone depth for clone cost;
     * both are reasonable defaults that a reachability check cannot rely on, because "this commit is not reachable from
     * any branch" is indistinguishable from "this mirror has not fetched recently" or "this mirror stops 100 commits
     * back". Use this to remove those two explanations before concluding the third.
     *
     * <p>Unshallowing is not undone afterwards: the repository stays full for the rest of the process. That is
     * deliberate — it makes the expense bounded per repository rather than repeatable on demand, so a caller cannot be
     * induced into paying it over and over.
     *
     * <p>No-op when the repository is not cached yet; the next {@code getOrClone} will clone it fresh anyway.
     *
     * @return true if a fetch was performed
     */
    public boolean refreshNow(
            String remoteUrl,
            CredentialsProvider credentials,
            TransportConfigCallback transportConfig,
            String principal)
            throws GitAPIException, IOException {
        String cacheKey = getCacheKey(remoteUrl);
        CachedRepository cached = cache.get(cacheKey);
        if (cached == null || !cached.isValid()) {
            return false;
        }
        cached.fetchLock.lock();
        try {
            log.info("Forcing upstream refresh for {} (cooldown bypassed, deepening to full history)", cacheKey);
            try (Git git = Git.open(new File(cacheDirectory.toFile(), cacheKey))) {
                var fetch = git.fetch()
                        .setRemote("origin")
                        .setRemoveDeletedRefs(true)
                        .setCredentialsProvider(credentials);
                if (transportConfig != null) fetch.setTransportConfigCallback(transportConfig);
                if (isShallow() && !cached.unshallowed) {
                    // Only meaningful on a mirror that was cloned shallow; harmless but pointless otherwise.
                    fetch.setUnshallow(true);
                }
                fetch.call();
            }
            cached.unshallowed = true;
            // The fetch reached upstream with these credentials, so this principal is verified — same
            // contract as refreshIfStale.
            long now = System.currentTimeMillis();
            cached.lastFetchedByPrincipal.put(hashPrincipal(principal), now);
            cached.lastSuccessfulFetchAt = now;
            return true;
        } finally {
            cached.fetchLock.unlock();
        }
    }

    /**
     * Hashes a caller-supplied principal so raw credentials never live in the cache's keys or heap dumps. Mirrors the
     * SCM token cache, which likewise stores only a digest. A blank or absent principal collapses to a single shared
     * anonymous identity — safe because an anonymous caller can only ever benefit from a previous *anonymous* fetch,
     * which by definition succeeded without credentials and so implies the repository is publicly readable.
     */
    private static String hashPrincipal(String principal) {
        if (principal == null || principal.isBlank()) {
            return ANONYMOUS_PRINCIPAL;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(principal.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable — cannot key the mirror cache by principal", e);
        }
    }

    /**
     * Re-fetches from upstream if the mirror hasn't been refreshed within {@code fetchCooldownMs}.
     *
     * <p>Acquires the per-repository fetch lock before checking the cooldown so that concurrent callers for the same
     * repository serialize — the second caller will see the updated {@code lastFetchedAt} and skip the fetch. This
     * prevents two simultaneous JGit fetch operations against the same bare repository directory, which can corrupt
     * ref/pack state under concurrent access.
     */
    private void refreshIfStale(
            CachedRepository cached,
            String cacheKey,
            CredentialsProvider credentials,
            TransportConfigCallback transportConfig,
            String principalKey)
            throws GitAPIException, IOException {
        cached.fetchLock.lock();
        try {
            cached.purgeExpiredPrincipals(fetchCooldownMs);
            Long lastFetched = cached.lastFetchedByPrincipal.get(principalKey);
            if (lastFetched != null && System.currentTimeMillis() - lastFetched <= fetchCooldownMs) {
                log.debug(
                        "Skipping re-fetch for {} — this principal refreshed the mirror {}ms ago",
                        cacheKey,
                        System.currentTimeMillis() - lastFetched);
                return;
            }
            log.debug(
                    "Re-fetching upstream for cached repository: {} — principal not verified within cooldown",
                    cacheKey);
            try (Git git = Git.open(new File(cacheDirectory.toFile(), cacheKey))) {
                var fetch = git.fetch()
                        .setRemote("origin")
                        .setRemoveDeletedRefs(true)
                        .setCredentialsProvider(credentials);
                if (transportConfig != null) fetch.setTransportConfigCallback(transportConfig);
                fetch.call();
            }
            // Recorded only after the fetch succeeds: reaching upstream with these credentials IS the
            // authorization proof. A failed fetch throws, so an unauthorized principal is never recorded
            // and never gets to skip the check on a subsequent request.
            long now = System.currentTimeMillis();
            cached.lastFetchedByPrincipal.put(principalKey, now);
            cached.lastSuccessfulFetchAt = now;
        } finally {
            cached.fetchLock.unlock();
        }
    }

    /**
     * Clone or fetch a repository. Serialized on a <em>per-repository</em> lock (keyed by cache key) so that two
     * threads racing on first access for the same URL don't produce duplicate parallel clones, while first clones of
     * different repositories run concurrently instead of queueing behind a single instance-wide monitor.
     */
    private Repository cloneOrFetch(
            String remoteUrl,
            String cacheKey,
            CredentialsProvider credentials,
            TransportConfigCallback transportConfig,
            String principalKey)
            throws GitAPIException, IOException {
        ReentrantLock cloneLock = cloneLocks.computeIfAbsent(cacheKey, k -> new ReentrantLock());
        cloneLock.lock();
        try {
            // Double-check after acquiring lock — another thread may have cloned while we waited
            CachedRepository cached = cache.get(cacheKey);
            if (cached != null && cached.isValid()) {
                cached.repository.incrementOpen();
                return cached.repository;
            }

            File repoDir = new File(cacheDirectory.toFile(), cacheKey);

            Repository repository;
            if (repoDir.exists()) {
                log.debug("Repository directory exists, opening and fetching: {}", repoDir);
                Git git = Git.open(repoDir);
                var fetchCmd = git.fetch().setRemote("origin");
                if (credentials != null) fetchCmd.setCredentialsProvider(credentials);
                if (transportConfig != null) fetchCmd.setTransportConfigCallback(transportConfig);
                if (shallowSince != null) {
                    fetchCmd.setShallowSince(Instant.now().minus(shallowSince));
                } else if (cloneDepth > 0) {
                    fetchCmd.setDepth(cloneDepth);
                }
                fetchCmd.call();
                repository = git.getRepository();
            } else {
                log.debug("Cloning repository to: {} ({})", repoDir, describeShallow());
                var cloneCommand = Git.cloneRepository()
                        .setURI(remoteUrl)
                        .setDirectory(repoDir)
                        .setBare(true);
                if (credentials != null) cloneCommand.setCredentialsProvider(credentials);
                if (transportConfig != null) cloneCommand.setTransportConfigCallback(transportConfig);
                if (shallowSince != null) {
                    cloneCommand.setShallowSince(Instant.now().minus(shallowSince));
                } else if (cloneDepth > 0) {
                    cloneCommand.setDepth(cloneDepth);
                }

                Git git = cloneCommand.call();
                repository = git.getRepository();
            }

            var newCached = new CachedRepository(repository, remoteUrl);
            // The clone/fetch above succeeded with this principal's credentials, so record them as verified.
            newCached.lastFetchedByPrincipal.put(principalKey, System.currentTimeMillis());
            newCached.lastSuccessfulFetchAt = newCached.cachedAt;
            cache.put(cacheKey, newCached);
            return repository;
        } finally {
            cloneLock.unlock();
        }
    }

    /**
     * Get a repository from cache without cloning if it doesn't exist.
     *
     * @param remoteUrl The URL of the remote repository
     * @return The local repository, or null if not cached
     */
    public Repository getCached(String remoteUrl) {
        String cacheKey = getCacheKey(remoteUrl);
        CachedRepository cached = cache.get(cacheKey);
        return (cached != null && cached.isValid()) ? cached.repository : null;
    }

    /**
     * Remove a repository from the cache and delete its local clone.
     *
     * @param remoteUrl The URL of the remote repository
     * @throws IOException If deletion fails
     */
    public void remove(String remoteUrl) throws IOException {
        removeByKey(getCacheKey(remoteUrl));
    }

    /**
     * Remove a single mirror by its cache key (the stable identifier surfaced by {@link #listEntries()}), deleting its
     * local clone from disk but leaving the cache root directory intact so subsequent clones still work.
     *
     * @param cacheKey the cache key, as produced by {@link #getCacheKey(String)} and reported in
     *     {@link CacheEntrySummary#cacheKey()}
     * @return {@code true} if an entry was present and removed, {@code false} if no entry matched the key
     * @throws IOException If deletion fails
     */
    public boolean removeByKey(String cacheKey) throws IOException {
        CachedRepository cached = cache.remove(cacheKey);
        if (cached == null) {
            return false;
        }
        cached.close();
        File repoDir = cached.repository.getDirectory();
        if (repoDir.exists()) {
            deleteDirectory(repoDir.toPath());
        }
        return true;
    }

    /**
     * Invalidate every mirror currently held, deleting each local clone but keeping the cache root directory (so the
     * live process can immediately re-clone on the next request). Unlike {@link #clear()} — which removes the root
     * directory and is intended for shutdown — this is safe to call on a running server.
     *
     * @return the number of mirrors invalidated
     * @throws IOException If deletion of any mirror fails
     */
    public int invalidateAll() throws IOException {
        int removed = 0;
        for (String key : cache.keySet()) {
            if (removeByKey(key)) {
                removed++;
            }
        }
        return removed;
    }

    /**
     * A read-only snapshot of every mirror currently held, for operator inspection (the admin cache view). Computing
     * on-disk size walks each mirror's directory and counting refs opens its ref database, so this is deliberately an
     * admin-triggered call, never on the push hot path. A mirror whose refs cannot be read (e.g. it was deleted out
     * from under us) is still listed, with {@code refCount == -1}, rather than failing the whole snapshot.
     *
     * @return one {@link CacheEntrySummary} per valid cached mirror, in no particular order
     */
    public List<CacheEntrySummary> listEntries() {
        List<CacheEntrySummary> entries = new ArrayList<>();
        for (Map.Entry<String, CachedRepository> e : cache.entrySet()) {
            CachedRepository cached = e.getValue();
            if (!cached.isValid()) {
                continue;
            }
            File repoDir = cached.repository.getDirectory();
            long sizeBytes = directorySize(repoDir.toPath());
            int refCount;
            try {
                refCount = cached.repository
                        .getRefDatabase()
                        .getRefsByPrefix(RefDatabase.ALL)
                        .size();
            } catch (IOException ex) {
                log.warn("Could not read refs for cached mirror {}: {}", e.getKey(), ex.getMessage());
                refCount = -1;
            }
            entries.add(new CacheEntrySummary(
                    e.getKey(),
                    cached.remoteUrl,
                    cached.cachedAt,
                    cached.lastSuccessfulFetchAt,
                    sizeBytes,
                    refCount,
                    isShallow(),
                    cached.unshallowed));
        }
        return entries;
    }

    /**
     * List the branches and tags present in one cached mirror. Returns an empty list when the key does not match a
     * valid entry, so a stale UICall (e.g. an entry invalidated between the list and the refs request) degrades to "no
     * refs" rather than an error.
     *
     * @param cacheKey the cache key from {@link CacheEntrySummary#cacheKey()}
     * @return the mirror's refs, or an empty list when the key is unknown or unreadable
     */
    public List<RefInfo> listRefs(String cacheKey) {
        CachedRepository cached = cache.get(cacheKey);
        if (cached == null || !cached.isValid()) {
            return List.of();
        }
        List<RefInfo> refs = new ArrayList<>();
        try {
            for (Ref ref : cached.repository.getRefDatabase().getRefsByPrefix(RefDatabase.ALL)) {
                String objectId = ref.getObjectId() != null ? ref.getObjectId().name() : "";
                refs.add(new RefInfo(ref.getName(), objectId, refType(ref.getName())));
            }
        } catch (IOException ex) {
            log.warn("Could not read refs for cached mirror {}: {}", cacheKey, ex.getMessage());
            return List.of();
        }
        return refs;
    }

    /** Classifies a ref name into a coarse type for display. */
    private static String refType(String name) {
        if (name.startsWith("refs/heads/")) return "branch";
        if (name.startsWith("refs/tags/")) return "tag";
        return "other";
    }

    /** Sum of the sizes of every regular file under {@code directory}; {@code 0} if it cannot be walked. */
    private static long directorySize(Path directory) {
        if (!Files.exists(directory)) {
            return 0L;
        }
        try (var stream = Files.walk(directory)) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException ex) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException ex) {
            log.warn("Could not compute size of cached mirror at {}: {}", directory, ex.getMessage());
            return 0L;
        }
    }

    /**
     * Clear the entire cache and delete all local clones.
     *
     * @throws IOException If cleanup fails
     */
    public void clear() throws IOException {
        for (String key : cache.keySet()) {
            CachedRepository cached = cache.remove(key);
            if (cached != null) {
                cached.close();
            }
        }
        deleteDirectory(cacheDirectory);
    }

    /** Cleanup resources on shutdown. */
    private void cleanup() {
        try {
            log.info("Cleaning up LocalRepositoryCache");
            clear();
        } catch (IOException e) {
            log.error("Error cleaning up LocalRepositoryCache", e);
        }
    }

    /**
     * Generate a cache key from a remote URL.
     *
     * <p>One cache instance is shared by every configured provider, so the key must cover the whole remote and not just
     * the path — otherwise {@code github.com/acme/app.git} and {@code gitea.internal/acme/app.git} are the same mirror.
     * The readable prefix is for identifying the directory on disk; the digest of the full URL is what guarantees
     * distinct remotes get distinct keys.
     *
     * @param remoteUrl The remote URL
     * @return A safe cache key
     */
    String getCacheKey(String remoteUrl) {
        try {
            URIish uri = new URIish(remoteUrl);
            String path = uri.getPath() != null ? uri.getPath() : "";
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.endsWith(".git")) {
                path = path.substring(0, path.length() - 4);
            }

            StringBuilder readable = new StringBuilder();
            if (uri.getHost() != null) {
                readable.append(uri.getHost());
                if (uri.getPort() > 0) {
                    readable.append('-').append(uri.getPort());
                }
                readable.append('_');
            }
            readable.append(path);

            return sanitizeKeySegment(readable.toString()) + "-" + shortDigest(remoteUrl);
        } catch (Exception e) {
            log.warn("Failed to parse remote URL, using hash as cache key: {}", remoteUrl);
            return "unparsed-" + shortDigest(remoteUrl);
        }
    }

    /** Reduce a key to characters that are safe as a single directory name on every supported platform. */
    private static String sanitizeKeySegment(String raw) {
        String safe = raw.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.length() > 100 ? safe.substring(0, 100) : safe;
    }

    /** First 12 hex chars of the SHA-256 of {@code value} — enough to keep distinct remotes in distinct directories. */
    private static String shortDigest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Delete a directory recursively.
     *
     * @param directory The directory to delete
     * @throws IOException If deletion fails
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        Files.walk(directory)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    /**
     * A read-only summary of one cached mirror for operator inspection.
     *
     * @param cacheKey stable key identifying the mirror; pass to {@link #listRefs(String)} / {@link #removeByKey}
     * @param remoteUrl the upstream URL this mirror clones
     * @param cachedAtMillis epoch millis when the mirror was first cloned into this process
     * @param lastFetchedAtMillis epoch millis of the last successful upstream fetch (equals {@code cachedAtMillis}
     *     until a subsequent re-fetch happens)
     * @param sizeBytes on-disk size of the local bare mirror
     * @param refCount number of refs in the mirror, or {@code -1} if they could not be read
     * @param shallow whether this cache clones shallow (by depth or time boundary)
     * @param unshallowed whether this specific mirror has since been deepened to full history on demand
     */
    public record CacheEntrySummary(
            String cacheKey,
            String remoteUrl,
            long cachedAtMillis,
            long lastFetchedAtMillis,
            long sizeBytes,
            int refCount,
            boolean shallow,
            boolean unshallowed) {}

    /**
     * One ref present in a cached mirror.
     *
     * @param name the full ref name, e.g. {@code refs/heads/main}
     * @param objectId the SHA the ref points at, or an empty string if unresolved
     * @param type coarse classification: {@code branch}, {@code tag}, or {@code other}
     */
    public record RefInfo(String name, String objectId, String type) {}

    /** Cached repository holder with per-repo fetch serialization. */
    private static class CachedRepository {
        final Repository repository;
        final String remoteUrl;
        final long cachedAt;

        /**
         * Serializes upstream fetches for this repository. Prevents two concurrent JGit fetch operations against the
         * same bare repository directory, which can interleave pack/ref writes and cause {@code want <sha> not valid}
         * errors during UploadPack negotiation.
         */
        final ReentrantLock fetchLock = new ReentrantLock();

        /**
         * Timestamp of the last successful upstream fetch, <b>per principal</b>. Compared against
         * {@code fetchCooldownMs} to avoid redundant re-fetches when the same caller makes several requests in quick
         * succession.
         *
         * <p>Keyed by principal rather than globally because a successful upstream fetch doubles as this path's only
         * proof that the caller may access the repository. A single shared timestamp would let any caller skip that
         * proof for {@code fetchCooldownMs} after someone else established it. Entries are purged once older than the
         * cooldown, so the map is bounded by the number of distinct principals active within one cooldown window.
         */
        final Map<String, Long> lastFetchedByPrincipal = new ConcurrentHashMap<>();

        /**
         * Set once {@link #refreshNow} has deepened this mirror to full history, so the expense is paid at most once
         * per repository per process rather than on every request that takes the deepening path.
         */
        volatile boolean unshallowed = false;

        /**
         * Wall-clock time of the last successful upstream fetch, for operator display only. Unlike
         * {@link #lastFetchedByPrincipal} (which is keyed by principal and purged after the cooldown), this is a single
         * monotonically-updated timestamp that survives for the life of the entry, so the admin cache view can always
         * show when the mirror last reached upstream. Never consulted for authorization. Initialised to
         * {@link #cachedAt} because the initial clone/fetch is itself a successful upstream contact.
         */
        volatile long lastSuccessfulFetchAt;

        CachedRepository(Repository repository, String remoteUrl) {
            this.repository = repository;
            this.remoteUrl = remoteUrl;
            this.cachedAt = System.currentTimeMillis();
            this.lastSuccessfulFetchAt = this.cachedAt;
        }

        /** Drops principals whose verification has aged past the cooldown; they must re-prove access on next use. */
        void purgeExpiredPrincipals(long cooldownMs) {
            long now = System.currentTimeMillis();
            lastFetchedByPrincipal.entrySet().removeIf(e -> now - e.getValue() > cooldownMs);
        }

        boolean isValid() {
            return repository != null && repository.getDirectory().exists();
        }

        void close() {
            if (repository != null) {
                repository.close();
            }
        }
    }
}
