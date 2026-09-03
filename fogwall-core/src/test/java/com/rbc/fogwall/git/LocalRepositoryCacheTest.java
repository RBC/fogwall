package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRepositoryCacheTest {

    @TempDir
    Path remoteTempDir;

    @TempDir
    Path cacheTempDir;

    Git remoteGit;
    String remoteUrl;

    @BeforeEach
    void setUp() throws Exception {
        // Create a non-bare remote repo with a commit so it can be cloned
        remoteGit = Git.init().setDirectory(remoteTempDir.toFile()).call();
        remoteGit.getRepository().getConfig().setBoolean("commit", null, "gpgsign", false);
        remoteGit.getRepository().getConfig().save();

        File f = new File(remoteTempDir.toFile(), "README.txt");
        Files.writeString(f.toPath(), "hello");
        remoteGit.add().addFilepattern(".").call();
        remoteGit
                .commit()
                .setAuthor(new PersonIdent("Dev", "dev@example.com"))
                .setCommitter(new PersonIdent("Dev", "dev@example.com"))
                .setMessage("Initial commit")
                .call();

        remoteUrl = remoteTempDir.toUri().toString();
    }

    @Test
    void coldMiss_clonesRepository() throws Exception {
        LocalRepositoryCache cache = new LocalRepositoryCache(cacheTempDir, false);

        Repository repo = cache.getOrClone(remoteUrl);

        assertNotNull(repo);
        assertTrue(repo.getDirectory().exists(), "Cloned repo directory should exist on disk");
        repo.close();
    }

    @Test
    void warmHit_returnsCachedWithoutRecloning() throws Exception {
        LocalRepositoryCache cache = new LocalRepositoryCache(cacheTempDir, false);

        Repository first = cache.getOrClone(remoteUrl);
        File firstDir = first.getDirectory();
        first.close();

        Repository second = cache.getOrClone(remoteUrl);
        File secondDir = second.getDirectory();
        second.close();

        assertEquals(
                firstDir.getCanonicalPath(),
                secondDir.getCanonicalPath(),
                "Second call should return the same cached clone");
    }

    @Test
    void getCached_beforeClone_returnsNull() throws Exception {
        LocalRepositoryCache cache = new LocalRepositoryCache(cacheTempDir, false);

        assertNull(cache.getCached(remoteUrl));
    }

    @Test
    void getCached_afterClone_returnsRepo() throws Exception {
        LocalRepositoryCache cache = new LocalRepositoryCache(cacheTempDir, false);
        Repository cloned = cache.getOrClone(remoteUrl);
        cloned.close();

        assertNotNull(cache.getCached(remoteUrl));
    }

    @Test
    void remove_evictsFromCacheAndDeletesDisk() throws Exception {
        LocalRepositoryCache cache = new LocalRepositoryCache(cacheTempDir, false);
        Repository cloned = cache.getOrClone(remoteUrl);
        File cloneDir = cloned.getDirectory();
        cloned.close();

        cache.remove(remoteUrl);

        assertNull(cache.getCached(remoteUrl), "Entry should be gone from cache");
        assertFalse(cloneDir.exists(), "Clone directory should be deleted from disk");
    }

    /**
     * The fetch cooldown must not let one principal's upstream verification carry over to another. A successful
     * upstream fetch is this path's only proof that the caller may access the repository, so an unrecognised principal
     * has to trigger a real fetch with its own credentials rather than being served the warm mirror.
     *
     * <p>Observed indirectly: a commit added upstream after the first call only reaches the mirror if a fetch actually
     * happened.
     */
    @Test
    void cooldown_isNotSharedBetweenPrincipals() throws Exception {
        // Long cooldown so any re-fetch is attributable to the principal changing, not to elapsed time.
        LocalRepositoryCache cache = new LocalRepositoryCache(cacheTempDir, 0, false, 60_000);

        Repository first = cache.getOrClone(remoteUrl, null, null, "alice:token-a");
        first.close();

        String newSha = addUpstreamCommit("second commit");

        Repository sameAgain = cache.getOrClone(remoteUrl, null, null, "alice:token-a");
        assertFalse(
                hasObject(sameAgain, newSha),
                "Same principal inside the cooldown should reuse the mirror without re-fetching");
        sameAgain.close();

        Repository other = cache.getOrClone(remoteUrl, null, null, "bob:token-b");
        assertTrue(
                hasObject(other, newSha),
                "A different principal must force a real upstream fetch — that fetch is the authorization check");
        other.close();
    }

    /** All callers without a principal share one anonymous identity, so they do share a cooldown with each other. */
    @Test
    void cooldown_isSharedAmongAnonymousCallers() throws Exception {
        LocalRepositoryCache cache = new LocalRepositoryCache(cacheTempDir, 0, false, 60_000);

        Repository first = cache.getOrClone(remoteUrl);
        first.close();

        String newSha = addUpstreamCommit("second commit");

        Repository second = cache.getOrClone(remoteUrl);
        assertFalse(hasObject(second, newSha), "Anonymous callers share one identity and therefore one cooldown");
        second.close();
    }

    /** An anonymous warm-up must not satisfy the cooldown for an identified principal, or vice versa. */
    @Test
    void anonymousWarmUp_doesNotSatisfyIdentifiedPrincipal() throws Exception {
        LocalRepositoryCache cache = new LocalRepositoryCache(cacheTempDir, 0, false, 60_000);

        Repository anon = cache.getOrClone(remoteUrl);
        anon.close();

        String newSha = addUpstreamCommit("second commit");

        Repository identified = cache.getOrClone(remoteUrl, null, null, "alice:token-a");
        assertTrue(hasObject(identified, newSha), "An identified principal must not inherit an anonymous verification");
        identified.close();
    }

    /** Once the cooldown lapses, even the same principal must re-verify against upstream. */
    @Test
    void cooldown_expiresForSamePrincipal() throws Exception {
        LocalRepositoryCache cache = new LocalRepositoryCache(cacheTempDir, 0, false, 0);

        Repository first = cache.getOrClone(remoteUrl, null, null, "alice:token-a");
        first.close();

        String newSha = addUpstreamCommit("second commit");

        Repository again = cache.getOrClone(remoteUrl, null, null, "alice:token-a");
        assertTrue(hasObject(again, newSha), "With the cooldown elapsed the same principal must re-fetch");
        again.close();
    }

    /**
     * True when the object is physically present in the mirror. {@code Repository.resolve} is unsuitable here: given a
     * full 40-character SHA it simply parses it and returns an ObjectId without consulting the object database, so it
     * answers "is this a well-formed id", not "was this fetched".
     */
    private static boolean hasObject(Repository repo, String sha) throws Exception {
        return repo.getObjectDatabase().has(ObjectId.fromString(sha));
    }

    /** Adds a commit to the upstream test repo and returns its SHA. */
    private String addUpstreamCommit(String message) throws Exception {
        File f = new File(remoteTempDir.toFile(), message.replace(' ', '-') + ".txt");
        Files.writeString(f.toPath(), message);
        remoteGit.add().addFilepattern(".").call();
        return remoteGit
                .commit()
                .setAuthor(new PersonIdent("Dev", "dev@example.com"))
                .setCommitter(new PersonIdent("Dev", "dev@example.com"))
                .setMessage(message)
                .call()
                .getName();
    }

    @Test
    void clear_removesAllEntriesAndDirectory() throws Exception {
        // Clone two different repos into the same cache
        Path remote2Dir = cacheTempDir.getParent().resolve(UUID.randomUUID().toString());
        Files.createDirectories(remote2Dir);
        Git remote2 = Git.init().setDirectory(remote2Dir.toFile()).call();
        remote2.getRepository().getConfig().setBoolean("commit", null, "gpgsign", false);
        remote2.getRepository().getConfig().save();
        File f2 = new File(remote2Dir.toFile(), "file.txt");
        Files.writeString(f2.toPath(), "second");
        remote2.add().addFilepattern(".").call();
        remote2.commit()
                .setAuthor(new PersonIdent("Dev", "dev@example.com"))
                .setCommitter(new PersonIdent("Dev", "dev@example.com"))
                .setMessage("init")
                .call();
        String remoteUrl2 = remote2Dir.toUri().toString();

        LocalRepositoryCache cache = new LocalRepositoryCache(cacheTempDir, false);
        Repository r1 = cache.getOrClone(remoteUrl);
        r1.close();
        Repository r2 = cache.getOrClone(remoteUrl2);
        r2.close();

        cache.clear();

        assertNull(cache.getCached(remoteUrl));
        assertNull(cache.getCached(remoteUrl2));
        assertFalse(cacheTempDir.toFile().exists(), "Cache directory should be deleted after clear");
    }

    @Test
    void sameRepoPathOnDifferentProviders_getsSeparateMirrors() throws Exception {
        LocalRepositoryCache cache = new LocalRepositoryCache(cacheTempDir, false);

        String github = cache.getCacheKey("https://github.com/acme/app.git");
        String internal = cache.getCacheKey("https://gitea.internal/acme/app.git");

        assertNotEquals(
                internal,
                github,
                "One cache is shared by every provider, so two providers hosting acme/app must not share a mirror");
    }

    @Test
    void sameHostOnDifferentPortsOrSchemes_getsSeparateMirrors() throws Exception {
        LocalRepositoryCache cache = new LocalRepositoryCache(cacheTempDir, false);

        assertNotEquals(
                cache.getCacheKey("https://scm.example.com:8443/acme/app.git"),
                cache.getCacheKey("https://scm.example.com/acme/app.git"),
                "Port is part of the remote's identity");
        assertNotEquals(
                cache.getCacheKey("ssh://scm.example.com/acme/app.git"),
                cache.getCacheKey("https://scm.example.com/acme/app.git"),
                "Scheme is part of the remote's identity");
    }

    @Test
    void sameUrl_getsTheSameKey() throws Exception {
        LocalRepositoryCache cache = new LocalRepositoryCache(cacheTempDir, false);

        assertEquals(
                cache.getCacheKey("https://github.com/acme/app.git"),
                cache.getCacheKey("https://github.com/acme/app.git"),
                "Key must be stable, or a warm mirror is never reused");
    }

    @Test
    void cacheKey_isAlwaysASingleDirectoryName() throws Exception {
        LocalRepositoryCache cache = new LocalRepositoryCache(cacheTempDir, false);

        for (String url : new String[] {
            "https://github.com/acme/app.git",
            "https://github.com/acme/../../etc/passwd.git",
            "https://github.com/acme/deeply/nested/group/app.git",
            "not a url at all"
        }) {
            String key = cache.getCacheKey(url);
            assertFalse(key.contains("/"), "Key must not contain a path separator: " + key);
            assertFalse(key.contains("\\"), "Key must not contain a path separator: " + key);
            assertEquals(
                    key,
                    new File(cacheTempDir.toFile(), key).getName(),
                    "Key must resolve to a direct child of the cache directory: " + key);
        }
    }

    // ---- clone depth / shallow-since (#476) ----

    /** Adds a commit dated {@code when} to the upstream repo (for shallow-since boundary tests). */
    private void addUpstreamCommitAt(String message, Instant when) throws Exception {
        File f = new File(remoteTempDir.toFile(), UUID.randomUUID() + ".txt");
        Files.writeString(f.toPath(), message);
        remoteGit.add().addFilepattern(".").call();
        PersonIdent id = new PersonIdent("Dev", "dev@example.com", when, ZoneOffset.UTC);
        remoteGit.commit().setAuthor(id).setCommitter(id).setMessage(message).call();
    }

    /** Counts commits reachable from HEAD in a (possibly shallow) mirror; the walk stops at the shallow boundary. */
    private static int reachableCommitCount(Repository repo) throws Exception {
        try (RevWalk walk = new RevWalk(repo)) {
            walk.markStart(walk.parseCommit(repo.resolve("HEAD")));
            int count = 0;
            for (RevCommit ignored : walk) {
                count++;
            }
            return count;
        }
    }

    @Test
    void fullClone_hasCompleteHistory() throws Exception {
        addUpstreamCommit("second");
        addUpstreamCommit("third");
        // depth 0 = full history
        LocalRepositoryCache cache = new LocalRepositoryCache(cacheTempDir, 0, false);

        Repository mirror = cache.getOrClone(remoteUrl);
        assertEquals(3, reachableCommitCount(mirror), "A full clone must mirror every commit");
        mirror.close();
    }

    @Test
    void shallowByDepth_truncatesHistory() throws Exception {
        addUpstreamCommit("second");
        addUpstreamCommit("third");
        LocalRepositoryCache cache = new LocalRepositoryCache(cacheTempDir, 1, false);

        Repository mirror = cache.getOrClone(remoteUrl);
        assertEquals(1, reachableCommitCount(mirror), "depth=1 must mirror only the tip commit");
        mirror.close();
    }

    @Test
    void shallowSince_keepsOnlyCommitsWithinTheBoundary() throws Exception {
        // Upstream already has the setUp "Initial commit" dated ~now. Add two old commits far outside the window.
        Instant now = Instant.now();
        addUpstreamCommitAt("old-1", now.minus(400, ChronoUnit.DAYS));
        addUpstreamCommitAt("recent", now.minus(1, ChronoUnit.DAYS));

        // shallow-since 90d: keep only history back to 90 days ago — the 400-day-old commit falls outside.
        LocalRepositoryCache cache = new LocalRepositoryCache(cacheTempDir, 0, false, Duration.ofDays(90));

        Repository mirror = cache.getOrClone(remoteUrl);
        int count = reachableCommitCount(mirror);
        assertTrue(count >= 1, "the recent tip must be present");
        assertTrue(count < 3, "a commit older than the shallow-since boundary must be excluded, got " + count);
        mirror.close();
    }

    @Test
    void refreshNow_deepensAShallowSinceMirrorToFullHistory() throws Exception {
        Instant now = Instant.now();
        addUpstreamCommitAt("old-1", now.minus(400, ChronoUnit.DAYS));
        addUpstreamCommitAt("recent", now.minus(1, ChronoUnit.DAYS));
        LocalRepositoryCache cache = new LocalRepositoryCache(cacheTempDir, 0, false, Duration.ofDays(90));

        Repository mirror = cache.getOrClone(remoteUrl, null, null, "alice:token");
        assertTrue(reachableCommitCount(mirror) < 3, "starts shallow");

        cache.refreshNow(remoteUrl, null, null, "alice:token");
        assertEquals(3, reachableCommitCount(mirror), "refreshNow must unshallow a shallow-since mirror to full");
        mirror.close();
    }
}
