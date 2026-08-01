package com.rbc.fogwall.git;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

/**
 * Manages local clones of remote repositories for inspection and filtering. This service uses JGit to clone
 * repositories into temporary directories and maintains a cache of these clones.
 *
 * <h2>Concurrency</h2>
 *
 * <p>Each cached repository has its own {@link ReentrantLock} ({@code CachedRepository.fetchLock}) that serializes
 * upstream fetches for that repository. The lock is acquired before any {@code git fetch} and released once the fetch
 * completes. A {@code fetchCooldownMs} guard ensures that a re-fetch is skipped when another thread has already
 * refreshed the mirror recently, avoiding redundant network round-trips.
 *
 * <p>Initial clones (first access for a URL) are still gated by an instance-level {@code synchronized} block to prevent
 * duplicate parallel clones of the same repository.
 *
 * <p>Note: upstream fetches are still possible while an {@code UploadPack} negotiation is in progress on the same
 * mirror. For deployments with high concurrent fetch traffic on shallow-cloned mirrors, consider using
 * {@code cloneDepth=0} for the serve cache to eliminate shallow-boundary reachability races entirely.
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
    private final int cloneDepth;
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
     * Full constructor with all options.
     *
     * @param cacheDirectory The directory to use for caching repositories
     * @param cloneDepth The depth for shallow clones (0 for full clone)
     * @param registerShutdownHook Whether to register shutdown hook (false for Spring apps)
     * @param fetchCooldownMs Minimum milliseconds between upstream re-fetches for the same repository
     */
    public LocalRepositoryCache(
            Path cacheDirectory, int cloneDepth, boolean registerShutdownHook, long fetchCooldownMs) {
        this.cacheDirectory = cacheDirectory;
        this.cloneDepth = cloneDepth;
        this.registerShutdownHook = registerShutdownHook;
        this.fetchCooldownMs = fetchCooldownMs;
        log.info("Initialized LocalRepositoryCache at: {} with clone depth: {}", cacheDirectory, cloneDepth);
        if (registerShutdownHook) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));
        }
    }

    /**
     * Get or create a local clone of a remote repository.
     *
     * @param remoteUrl The URL of the remote repository
     * @return The local repository
     * @throws GitAPIException If git operations fail
     * @throws IOException If I/O operations fail
     */
    public Repository getOrClone(String remoteUrl) throws GitAPIException, IOException {
        return getOrClone(remoteUrl, null);
    }

    /**
     * Get or create a local clone of a remote repository, using the supplied credentials for clone and fetch.
     * Credentials are passed transiently to JGit — they are never written to disk.
     *
     * <p>On a cache hit, re-fetches from upstream to keep the local mirror fresh. The re-fetch is serialized via a
     * per-repository lock and skipped if the mirror was already refreshed within {@code fetchCooldownMs}.
     */
    public Repository getOrClone(String remoteUrl, CredentialsProvider credentials)
            throws GitAPIException, IOException {
        return getOrClone(remoteUrl, credentials, null);
    }

    /**
     * Get or create a local clone of a remote repository, with an optional {@link TransportConfigCallback} applied to
     * every clone and fetch operation. Use this overload when per-request transport configuration is needed (e.g. SSH
     * agent forwarding) — the callback is scoped to this call and never stored globally.
     */
    public Repository getOrClone(
            String remoteUrl, CredentialsProvider credentials, TransportConfigCallback transportConfig)
            throws GitAPIException, IOException {
        return getOrClone(remoteUrl, credentials, transportConfig, null);
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
            cached.lastFetchedByPrincipal.put(principalKey, System.currentTimeMillis());
        } finally {
            cached.fetchLock.unlock();
        }
    }

    /**
     * Clone or fetch a repository. Synchronized at the instance level to prevent duplicate parallel clones when
     * multiple threads race on first access for the same URL.
     */
    private synchronized Repository cloneOrFetch(
            String remoteUrl,
            String cacheKey,
            CredentialsProvider credentials,
            TransportConfigCallback transportConfig,
            String principalKey)
            throws GitAPIException, IOException {
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
            if (cloneDepth > 0) fetchCmd.setDepth(cloneDepth);
            fetchCmd.call();
            repository = git.getRepository();
        } else {
            log.debug("Cloning repository to: {} with depth: {}", repoDir, cloneDepth);
            var cloneCommand = Git.cloneRepository()
                    .setURI(remoteUrl)
                    .setDirectory(repoDir)
                    .setBare(true);
            if (credentials != null) cloneCommand.setCredentialsProvider(credentials);
            if (transportConfig != null) cloneCommand.setTransportConfigCallback(transportConfig);
            if (cloneDepth > 0) cloneCommand.setDepth(cloneDepth);

            Git git = cloneCommand.call();
            repository = git.getRepository();
        }

        var newCached = new CachedRepository(repository, remoteUrl);
        // The clone/fetch above succeeded with this principal's credentials, so record them as verified.
        newCached.lastFetchedByPrincipal.put(principalKey, System.currentTimeMillis());
        cache.put(cacheKey, newCached);
        return repository;
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
        String cacheKey = getCacheKey(remoteUrl);
        CachedRepository cached = cache.remove(cacheKey);
        if (cached != null) {
            cached.close();
            File repoDir = cached.repository.getDirectory();
            if (repoDir.exists()) {
                deleteDirectory(repoDir.toPath());
            }
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
     * @param remoteUrl The remote URL
     * @return A safe cache key
     */
    private String getCacheKey(String remoteUrl) {
        try {
            URIish uri = new URIish(remoteUrl);
            String path = uri.getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.endsWith(".git")) {
                path = path.substring(0, path.length() - 4);
            }
            return path.replace("/", "_").replace("\\", "_");
        } catch (Exception e) {
            log.warn("Failed to parse remote URL, using hash as cache key: {}", remoteUrl);
            return String.valueOf(remoteUrl.hashCode());
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

        CachedRepository(Repository repository, String remoteUrl) {
            this.repository = repository;
            this.remoteUrl = remoteUrl;
            this.cachedAt = System.currentTimeMillis();
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
