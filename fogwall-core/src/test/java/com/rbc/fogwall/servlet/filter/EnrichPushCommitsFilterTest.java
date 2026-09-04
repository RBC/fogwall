package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;
import static java.nio.file.Files.writeString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.git.LocalRepositoryCache;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.servlet.RequestBodyWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PacketLineOut;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * Integration tests for {@link EnrichPushCommitsFilter}.
 *
 * <p>Uses real JGit repositories (via {@code @TempDir}) and a mocked {@link LocalRepositoryCache}. The happy-path test
 * verifies the full pipeline: pack unpack → commit walk → populate {@code pushedCommits}. Additional tests cover the
 * "objects already in cache" path and the short-circuit cases (null details, empty toCommit).
 */
class EnrichPushCommitsFilterTest {

    @TempDir
    Path sourceDir;

    @TempDir
    Path cacheDir;

    @TempDir
    Path cacheDir2;

    @TempDir
    Path cacheDir3;

    @TempDir
    Path sourceDir2;

    @TempDir
    Path quarantineSourceDir;

    @TempDir
    Path quarantineMirrorDir;

    // Minimal ServletInputStream backed by a byte array - mirrors ParseGitRequestFilterTest.
    private static class MockServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream is;

        MockServletInputStream(byte[] data) {
            this.is = new ByteArrayInputStream(data);
        }

        @Override
        public int read() throws IOException {
            return is.read();
        }

        @Override
        public boolean isFinished() {
            return is.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener l) {}
    }

    /**
     * Wrap {@code body} and a pre-built {@link GitRequestDetails} into a {@link RequestBodyWrapper}. The wrapper is
     * what {@link EnrichPushCommitsFilter} expects: it reads the body via {@code getBody()} and the request details via
     * {@code getAttribute(GIT_REQUEST_ATTR)}.
     */
    /** A response mock that can actually receive {@code sendGitError}'s output — mirrors CheckEmptyBranchFilterTest. */
    private static HttpServletResponse fakeResponse() throws IOException {
        HttpServletResponse mock = mock(HttpServletResponse.class);
        when(mock.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) {}

            @Override
            public void write(byte[] b, int off, int len) {}

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener l) {}
        });
        return mock;
    }

    private RequestBodyWrapper wrapRequest(byte[] body, GitRequestDetails details) throws IOException {
        HttpServletRequest inner = mock(HttpServletRequest.class);
        when(inner.getMethod()).thenReturn("POST");
        when(inner.getContentType()).thenReturn("application/x-git-receive-pack-request");
        when(inner.getRequestURI()).thenReturn("/proxy/github.com/owner/repo.git/git-receive-pack");
        when(inner.getPathInfo()).thenReturn("/owner/repo.git/git-receive-pack");
        when(inner.getInputStream()).thenReturn(new MockServletInputStream(body));
        Enumeration<String> emptyEnum = Collections.emptyEnumeration();
        when(inner.getHeaderNames()).thenReturn(emptyEnum);
        when(inner.getAttribute(GIT_REQUEST_ATTR)).thenReturn(details);
        return new RequestBodyWrapper(inner);
    }

    private GitRequestDetails makeDetails(String fromSha, String toSha) {
        GitRequestDetails details = new GitRequestDetails();
        details.setOperation(HttpOperation.PUSH);
        details.setCommitFrom(fromSha);
        details.setCommitTo(toSha);
        details.setRepoRef(GitRequestDetails.RepoRef.builder()
                .owner("owner")
                .name("repo")
                .slug("/owner/repo")
                .build());
        return details;
    }

    /**
     * Insert a minimal commit (blob → tree → commit) directly into a repository's object store and return the commit
     * SHA. Used to pre-populate a bare cache repo so the pack-unpack step can be skipped.
     */
    private String insertCommit(Repository repo) throws Exception {
        try (ObjectInserter inserter = repo.newObjectInserter()) {
            ObjectId blobId = inserter.insert(Constants.OBJ_BLOB, "hello world".getBytes());
            TreeFormatter tree = new TreeFormatter();
            tree.append("hello.txt", FileMode.REGULAR_FILE, blobId);
            ObjectId treeId = inserter.insert(tree);

            CommitBuilder commit = new CommitBuilder();
            commit.setTreeId(treeId);
            PersonIdent ident = new PersonIdent("Author", "author@example.com");
            commit.setAuthor(ident);
            commit.setCommitter(ident);
            commit.setMessage("test commit\n");
            ObjectId commitId = inserter.insert(commit);
            inserter.flush();
            return commitId.getName();
        }
    }

    /**
     * Like {@link #insertCommit(Repository)} but also stores the commit under {@code refs/heads/main}. Required for tag
     * push tests: {@link com.rbc.fogwall.git.CommitInspectionService#getCommitRange} excludes commits reachable from
     * {@code refs/heads/*}, so storing the commit there makes it appear "already upstream" to the range walk.
     */
    private String insertCommitAsUpstream(Repository repo) throws Exception {
        String sha = insertCommit(repo);
        RefUpdate ru = repo.updateRef("refs/heads/main");
        ru.setNewObjectId(ObjectId.fromString(sha));
        ru.setExpectedOldObjectId(ObjectId.zeroId());
        ru.update();
        return sha;
    }

    /**
     * The client's credentials must reach the upstream clone/fetch — without them a private repository fails with an
     * upstream 404 that surfaces to the developer as "repository not found".
     *
     * <p>Just as importantly, a <b>principal</b> must be passed alongside them. The mirror cache keys its fetch
     * cooldown by {@code (repository, principal)}; supplying credentials while leaving the principal null would put
     * every proxy caller on the shared anonymous entry, letting one user's successful fetch satisfy the next user's
     * request. The principal is built from the whole credential rather than the username because providers such as
     * GitHub ignore the HTTP Basic username entirely.
     */
    @Test
    void doHttpFilter_passesClientCredentialsAndPrincipalToCache() throws Exception {
        Repository cacheRepo =
                Git.init().setBare(true).setDirectory(cacheDir.toFile()).call().getRepository();
        String toSha = insertCommit(cacheRepo);

        LocalRepositoryCache mockCache = mock(LocalRepositoryCache.class);
        when(mockCache.getOrClone(any(), any(), any(), any())).thenReturn(cacheRepo);

        GitRequestDetails details = makeDetails(ObjectId.zeroId().name(), toSha);
        RequestBodyWrapper request = wrapRequest(new byte[0], details);
        String basic = Base64.getEncoder().encodeToString("alice:ghp_secrettoken".getBytes(StandardCharsets.UTF_8));
        when(((HttpServletRequest) request.getRequest()).getHeader("Authorization"))
                .thenReturn("Basic " + basic);

        new EnrichPushCommitsFilter(new GitHubProvider("/proxy"), mockCache)
                .doHttpFilter(request, mock(HttpServletResponse.class));

        ArgumentCaptor<CredentialsProvider> creds = ArgumentCaptor.forClass(CredentialsProvider.class);
        ArgumentCaptor<String> principal = ArgumentCaptor.forClass(String.class);
        verify(mockCache).getOrClone(any(), creds.capture(), any(), principal.capture());

        assertNotNull(creds.getValue(), "Client credentials must be forwarded to the upstream clone/fetch");
        assertEquals(
                "alice:ghp_secrettoken",
                principal.getValue(),
                "Principal must be the full credential, so the cooldown cannot be shared between callers");
    }

    /** With no Authorization header the caller is genuinely anonymous — no credentials and no principal. */
    @Test
    void doHttpFilter_withoutAuthHeader_passesNoCredentialsOrPrincipal() throws Exception {
        Repository cacheRepo =
                Git.init().setBare(true).setDirectory(cacheDir.toFile()).call().getRepository();
        String toSha = insertCommit(cacheRepo);

        LocalRepositoryCache mockCache = mock(LocalRepositoryCache.class);
        when(mockCache.getOrClone(any(), any(), any(), any())).thenReturn(cacheRepo);

        GitRequestDetails details = makeDetails(ObjectId.zeroId().name(), toSha);
        RequestBodyWrapper request = wrapRequest(new byte[0], details);

        new EnrichPushCommitsFilter(new GitHubProvider("/proxy"), mockCache)
                .doHttpFilter(request, mock(HttpServletResponse.class));

        ArgumentCaptor<CredentialsProvider> creds = ArgumentCaptor.forClass(CredentialsProvider.class);
        ArgumentCaptor<String> principal = ArgumentCaptor.forClass(String.class);
        verify(mockCache).getOrClone(any(), creds.capture(), any(), principal.capture());

        assertNull(creds.getValue());
        assertNull(principal.getValue());
    }

    /**
     * Happy path (no pack): commit objects are already in the bare cache (e.g. the cache was recently cloned). The
     * filter skips pack unpack because the request body is empty, but can still walk the commit range.
     */
    @Test
    void doHttpFilter_objectsAlreadyInCache_populatesCommits() throws Exception {
        Repository cacheRepo =
                Git.init().setBare(true).setDirectory(cacheDir.toFile()).call().getRepository();
        String toSha = insertCommit(cacheRepo);
        String fromSha = ObjectId.zeroId().name();

        LocalRepositoryCache mockCache = mock(LocalRepositoryCache.class);
        when(mockCache.getOrClone(any(), any(), any(), any())).thenReturn(cacheRepo);

        GitRequestDetails details = makeDetails(fromSha, toSha);

        // Empty body - no PACK signature; unpackPushData short-circuits and objects are found from the pre-insert.
        RequestBodyWrapper request = wrapRequest(new byte[0], details);
        HttpServletResponse response = mock(HttpServletResponse.class);

        new EnrichPushCommitsFilter(new GitHubProvider("/proxy"), mockCache).doHttpFilter(request, response);

        assertFalse(details.getPushedCommits().isEmpty(), "Expected pushed commits to be populated");
        assertEquals(toSha, details.getPushedCommits().get(0).getSha());
    }

    /**
     * Full pipeline: the filter receives raw pack bytes as the request body, unpacks them into an initially-empty bare
     * cache repo, then walks the commit range to populate {@code pushedCommits}.
     */
    @Test
    void doHttpFilter_populatesCommitsFromPackData() throws Exception {
        // Build a source repo and commit a file.
        Git sourceGit = Git.init().setDirectory(sourceDir.toFile()).call();
        sourceGit.getRepository().getConfig().setBoolean("commit", null, "gpgsign", false);
        sourceGit.getRepository().getConfig().save();
        writeString(sourceDir.resolve("hello.txt"), "hello world");
        sourceGit.add().addFilepattern("hello.txt").call();
        var revCommit = sourceGit
                .commit()
                .setMessage("pack test commit")
                .setAuthor("Author", "author@example.com")
                .call();
        String toSha = revCommit.getName();
        String fromSha = ObjectId.zeroId().name();

        // Generate a pack containing the new commit and all its reachable objects.
        ByteArrayOutputStream packOut = new ByteArrayOutputStream();
        try (PackWriter packWriter = new PackWriter(sourceGit.getRepository())) {
            packWriter.setDeltaBaseAsOffset(false);
            packWriter.preparePack(NullProgressMonitor.INSTANCE, Set.of(ObjectId.fromString(toSha)), Set.of());
            packWriter.writePack(NullProgressMonitor.INSTANCE, NullProgressMonitor.INSTANCE, packOut);
        }

        // Empty bare cache - objects will be inserted by the filter via PackParser.
        Repository cacheRepo =
                Git.init().setBare(true).setDirectory(cacheDir2.toFile()).call().getRepository();
        LocalRepositoryCache mockCache = mock(LocalRepositoryCache.class);
        when(mockCache.getOrClone(any(), any(), any(), any())).thenReturn(cacheRepo);

        GitRequestDetails details = makeDetails(fromSha, toSha);

        // The pack bytes start with "PACK" - findPackSignature finds offset 0.
        RequestBodyWrapper request = wrapRequest(packOut.toByteArray(), details);
        HttpServletResponse response = mock(HttpServletResponse.class);

        new EnrichPushCommitsFilter(new GitHubProvider("/proxy"), mockCache).doHttpFilter(request, response);

        assertFalse(details.getPushedCommits().isEmpty(), "Expected pushed commits after pack unpack");
        assertEquals(toSha, details.getPushedCommits().get(0).getSha());
    }

    /**
     * When {@code GitRequestDetails} attribute is absent the filter should return silently without touching the cache.
     */
    @Test
    void doHttpFilter_noRequestDetails_doesNotThrow() throws Exception {
        LocalRepositoryCache mockCache = mock(LocalRepositoryCache.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(GIT_REQUEST_ATTR)).thenReturn(null);

        assertDoesNotThrow(() -> new EnrichPushCommitsFilter(new GitHubProvider("/proxy"), mockCache)
                .doHttpFilter(request, mock(HttpServletResponse.class)));
        verifyNoInteractions(mockCache);
    }

    /** When {@code toCommit} is blank the filter short-circuits before touching the cache. */
    @Test
    void doHttpFilter_emptyToCommit_doesNotCallCache() throws Exception {
        LocalRepositoryCache mockCache = mock(LocalRepositoryCache.class);
        GitRequestDetails details = makeDetails(ObjectId.zeroId().name(), "");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(GIT_REQUEST_ATTR)).thenReturn(details);

        new EnrichPushCommitsFilter(new GitHubProvider("/proxy"), mockCache)
                .doHttpFilter(request, mock(HttpServletResponse.class));

        verifyNoInteractions(mockCache);
        assertTrue(details.getPushedCommits().isEmpty());
    }

    // ---- CVE-2025-54584: PACK signature spoofing via ref name ----

    /**
     * Verify that a request body containing "PACK" in the ref name does not confuse the pack data offset detection. The
     * filter must walk past pkt-lines and find the real PACK data after the flush packet.
     */
    @Test
    void doHttpFilter_packInRefName_stillUnpacksCorrectly() throws Exception {
        // Build a source repo and commit a file.
        Git sourceGit = Git.init().setDirectory(sourceDir.toFile()).call();
        sourceGit.getRepository().getConfig().setBoolean("commit", null, "gpgsign", false);
        sourceGit.getRepository().getConfig().save();
        writeString(sourceDir.resolve("hello.txt"), "hello world");
        sourceGit.add().addFilepattern("hello.txt").call();
        var revCommit = sourceGit
                .commit()
                .setMessage("PACK spoofing test")
                .setAuthor("Author", "author@example.com")
                .call();
        String toSha = revCommit.getName();
        String fromSha = ObjectId.zeroId().name();

        // Generate a valid pack
        ByteArrayOutputStream packOut = new ByteArrayOutputStream();
        try (PackWriter packWriter = new PackWriter(sourceGit.getRepository())) {
            packWriter.setDeltaBaseAsOffset(false);
            packWriter.preparePack(NullProgressMonitor.INSTANCE, Set.of(ObjectId.fromString(toSha)), Set.of());
            packWriter.writePack(NullProgressMonitor.INSTANCE, NullProgressMonitor.INSTANCE, packOut);
        }

        // Build a full request body with "PACK" in the ref name — this is the attack vector
        ByteArrayOutputStream bodyOut = new ByteArrayOutputStream();
        PacketLineOut plo = new PacketLineOut(bodyOut);
        plo.writeString(fromSha + " " + toSha + " refs/heads/PACK-evil\0 report-status side-band-64k");
        plo.end();
        bodyOut.write(packOut.toByteArray());

        // Set up cache repo
        Repository cacheRepo =
                Git.init().setBare(true).setDirectory(cacheDir2.toFile()).call().getRepository();
        LocalRepositoryCache mockCache = mock(LocalRepositoryCache.class);
        when(mockCache.getOrClone(any(), any(), any(), any())).thenReturn(cacheRepo);

        GitRequestDetails details = makeDetails(fromSha, toSha);

        RequestBodyWrapper request = wrapRequest(bodyOut.toByteArray(), details);
        HttpServletResponse response = mock(HttpServletResponse.class);

        new EnrichPushCommitsFilter(new GitHubProvider("/proxy"), mockCache).doHttpFilter(request, response);

        assertFalse(details.getPushedCommits().isEmpty(), "Pack must be unpacked despite PACK in ref name");
        assertEquals(toSha, details.getPushedCommits().get(0).getSha());
    }

    // ---- fail-closed behaviour ----

    // ---- Tag push handling (#337) ----

    /**
     * Normal tag push: annotated tag pointing to a commit that already exists upstream. The commit range is empty
     * (nothing new to validate). localRepository must be set so CheckHiddenCommitsFilter can run. Result stays PENDING.
     */
    @Test
    void doHttpFilter_tagPush_existingCommit_passesThrough() throws Exception {
        // Bare cache with a pre-existing commit (simulates the upstream state after the branch was pushed).
        Repository cacheRepo =
                Git.init().setBare(true).setDirectory(cacheDir3.toFile()).call().getRepository();
        String commitSha = insertCommitAsUpstream(cacheRepo);

        // Create an annotated tag object in the cache pointing at that commit.
        try (ObjectInserter ins = cacheRepo.newObjectInserter()) {
            TagBuilder tag = new TagBuilder();
            tag.setTag("v1.0.0");
            tag.setObjectId(ObjectId.fromString(commitSha), Constants.OBJ_COMMIT);
            tag.setTagger(new PersonIdent("Tagger", "tagger@example.com"));
            tag.setMessage("Release v1.0.0\n");
            ObjectId tagId = ins.insert(tag);
            ins.flush();

            // The push packet reports the tag object SHA as toCommit.
            LocalRepositoryCache mockCache = mock(LocalRepositoryCache.class);
            when(mockCache.getOrClone(any(), any(), any(), any())).thenReturn(cacheRepo);

            GitRequestDetails details = makeDetails(ObjectId.zeroId().name(), tagId.getName());
            details.setBranch("refs/tags/v1.0.0");

            RequestBodyWrapper request = wrapRequest(new byte[0], details);

            new EnrichPushCommitsFilter(new GitHubProvider("/proxy"), mockCache)
                    .doHttpFilter(request, mock(HttpServletResponse.class));

            assertEquals(
                    GitRequestDetails.GitResult.PENDING,
                    details.getResult(),
                    "Should pass through — commit already upstream");
            assertTrue(details.getPushedCommits().isEmpty(), "No new commits expected");
            assertSameGitDir(
                    cacheRepo,
                    details.getLocalRepository(),
                    "localRepository must be set for CheckHiddenCommitsFilter");
        }
    }

    /**
     * Lightweight tag pointing to an existing commit. A lightweight tag ref points directly to a commit SHA (no tag
     * object), so ^{commit} peeling is a no-op. Result stays PENDING.
     */
    @Test
    void doHttpFilter_lightweightTagPush_existingCommit_passesThrough() throws Exception {
        Repository cacheRepo =
                Git.init().setBare(true).setDirectory(cacheDir.toFile()).call().getRepository();
        String commitSha = insertCommitAsUpstream(cacheRepo);

        LocalRepositoryCache mockCache = mock(LocalRepositoryCache.class);
        when(mockCache.getOrClone(any(), any(), any(), any())).thenReturn(cacheRepo);

        // Lightweight tag: toCommit is the commit SHA directly (no tag object).
        GitRequestDetails details = makeDetails(ObjectId.zeroId().name(), commitSha);
        details.setBranch("refs/tags/v1.0.0");

        RequestBodyWrapper request = wrapRequest(new byte[0], details);

        new EnrichPushCommitsFilter(new GitHubProvider("/proxy"), mockCache)
                .doHttpFilter(request, mock(HttpServletResponse.class));

        assertEquals(GitRequestDetails.GitResult.PENDING, details.getResult());
        assertTrue(details.getPushedCommits().isEmpty());
        assertSameGitDir(cacheRepo, details.getLocalRepository(), "localRepository must be set");
    }

    /**
     * Tag push introducing new commits that were never pushed through a branch — rejected. A tag should only reference
     * commits that were already validated via a branch push. Accepting unvalidated commits through a tag ref would
     * allow bypassing the proxy's commit validation (#337).
     */
    @Test
    void doHttpFilter_tagPush_newCommits_rejected() throws Exception {
        // Build a source repo with a commit not yet in the cache.
        Git sourceGit = Git.init().setDirectory(sourceDir2.toFile()).call();
        sourceGit.getRepository().getConfig().setBoolean("commit", null, "gpgsign", false);
        sourceGit.getRepository().getConfig().save();
        writeString(sourceDir2.resolve("secret.txt"), "new content");
        sourceGit.add().addFilepattern("secret.txt").call();
        var revCommit = sourceGit
                .commit()
                .setMessage("unvalidated commit")
                .setAuthor("Author", "author@example.com")
                .call();
        String commitSha = revCommit.getName();

        // Generate a pack containing the new commit.
        ByteArrayOutputStream packOut = new ByteArrayOutputStream();
        try (PackWriter pw = new PackWriter(sourceGit.getRepository())) {
            pw.setDeltaBaseAsOffset(false);
            pw.preparePack(NullProgressMonitor.INSTANCE, Set.of(ObjectId.fromString(commitSha)), Set.of());
            pw.writePack(NullProgressMonitor.INSTANCE, NullProgressMonitor.INSTANCE, packOut);
        }

        // Empty cache — the commit doesn't exist upstream.
        Repository cacheRepo =
                Git.init().setBare(true).setDirectory(cacheDir2.toFile()).call().getRepository();
        LocalRepositoryCache mockCache = mock(LocalRepositoryCache.class);
        when(mockCache.getOrClone(any(), any(), any(), any())).thenReturn(cacheRepo);

        // Lightweight tag pointing directly at the new commit.
        GitRequestDetails details = makeDetails(ObjectId.zeroId().name(), commitSha);
        details.setBranch("refs/tags/evil-tag");

        RequestBodyWrapper request = wrapRequest(packOut.toByteArray(), details);

        new EnrichPushCommitsFilter(new GitHubProvider("/proxy"), mockCache).doHttpFilter(request, fakeResponse());

        assertEquals(
                GitRequestDetails.GitResult.ERROR, details.getResult(), "Tag push with new commits must be rejected");
        assertNotNull(details.getReason());
        assertTrue(details.getReason().contains("branch"), "Error must direct the user to push the branch first");
        assertTrue(details.getPushedCommits().isEmpty());
        assertSameGitDir(cacheRepo, details.getLocalRepository(), "localRepository must still be set");
    }

    /**
     * Tag push where the tag object SHA cannot be resolved to a commit after unpacking (e.g. malformed pack, or the tag
     * object was deliberately omitted). A legitimate git client always includes the tag object in the pack — a missing
     * object is suspicious and must be rejected to fail closed.
     */
    @Test
    void doHttpFilter_tagPush_unresolvableTagObject_rejected() throws Exception {
        Repository emptyRepo =
                Git.init().setBare(true).setDirectory(cacheDir3.toFile()).call().getRepository();
        LocalRepositoryCache mockCache = mock(LocalRepositoryCache.class);
        when(mockCache.getOrClone(any(), any(), any(), any())).thenReturn(emptyRepo);

        // A tag SHA that does not exist in the cache — simulates a missing/omitted tag object.
        GitRequestDetails details = makeDetails(ObjectId.zeroId().name(), "07697092d36d3323eaaf4be18b3a5b1f276ab0dc");
        details.setBranch("refs/tags/v1.1.0");

        RequestBodyWrapper request = wrapRequest(new byte[0], details);

        new EnrichPushCommitsFilter(new GitHubProvider("/proxy"), mockCache).doHttpFilter(request, fakeResponse());

        assertEquals(
                GitRequestDetails.GitResult.ERROR, details.getResult(), "Unresolvable tag object must be rejected");
        assertNotNull(details.getReason());
        assertTrue(details.getPushedCommits().isEmpty());
        assertSameGitDir(emptyRepo, details.getLocalRepository(), "localRepository must be set");
    }

    /** Ref deletions (commitTo = all zeros) must skip enrichment entirely and leave result PENDING. */
    @Test
    void doHttpFilter_refDeletion_skipsEnrichment() throws Exception {
        LocalRepositoryCache mockCache = mock(LocalRepositoryCache.class);
        GitRequestDetails details =
                makeDetails(ObjectId.zeroId().name(), ObjectId.zeroId().name());

        RequestBodyWrapper request = wrapRequest(new byte[0], details);
        HttpServletResponse response = mock(HttpServletResponse.class);

        new EnrichPushCommitsFilter(new GitHubProvider("/proxy"), mockCache).doHttpFilter(request, response);

        verifyNoInteractions(mockCache);
        assertTrue(details.getPushedCommits().isEmpty());
        assertEquals(GitRequestDetails.GitResult.PENDING, details.getResult());
    }

    /** If the cache throws an exception, the push must be marked ERROR — not silently passed through. */
    @Test
    void doHttpFilter_cacheThrows_errorsRequest() throws Exception {
        LocalRepositoryCache mockCache = mock(LocalRepositoryCache.class);
        when(mockCache.getOrClone(any(), any(), any(), any())).thenThrow(new RuntimeException("network unreachable"));

        GitRequestDetails details = makeDetails(ObjectId.zeroId().name(), "abc1234abc1234abc1234abc1234abc1234abc123");

        RequestBodyWrapper request = wrapRequest(new byte[0], details);

        new EnrichPushCommitsFilter(new GitHubProvider("/proxy"), mockCache).doHttpFilter(request, fakeResponse());

        assertEquals(GitRequestDetails.GitResult.ERROR, details.getResult());
        assertNotNull(details.getReason());
        assertTrue(details.getPushedCommits().isEmpty());
    }

    /** An empty commit range (objects not resolvable) must error the push, not pass it through. */
    @Test
    void doHttpFilter_emptyCommitRange_errorsRequest() throws Exception {
        // Bare cache repo with no objects — commit range will resolve to empty.
        Repository emptyRepo =
                Git.init().setBare(true).setDirectory(cacheDir.toFile()).call().getRepository();
        LocalRepositoryCache mockCache = mock(LocalRepositoryCache.class);
        when(mockCache.getOrClone(any(), any(), any(), any())).thenReturn(emptyRepo);

        String toSha = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
        GitRequestDetails details = makeDetails(ObjectId.zeroId().name(), toSha);

        RequestBodyWrapper request = wrapRequest(new byte[0], details);

        new EnrichPushCommitsFilter(new GitHubProvider("/proxy"), mockCache).doHttpFilter(request, fakeResponse());

        assertEquals(GitRequestDetails.GitResult.ERROR, details.getResult());
        assertNotNull(details.getReason());
        assertTrue(details.getPushedCommits().isEmpty());
    }

    /**
     * The point of M6: the whole request runs, the push is inspected, and when it is over the shared mirror holds
     * nothing the push brought in. Goes through {@code doFilter} rather than {@code doHttpFilter} because teardown is
     * tied to the chain completing, not to this filter's own body finishing.
     */
    @Test
    void pushedObjectsAreGoneFromTheMirrorOnceTheRequestEnds() throws Exception {
        Path srcDir = Files.createDirectories(quarantineSourceDir.resolve("src"));
        Git sourceGit = Git.init().setDirectory(srcDir.toFile()).call();
        writeString(srcDir.resolve("secret.txt"), "leaked credential\n");
        sourceGit.add().addFilepattern("secret.txt").call();
        var revCommit = sourceGit
                .commit()
                .setMessage("would be rejected")
                .setAuthor("Author", "author@example.com")
                .setSign(false)
                .call();
        String toSha = revCommit.getName();
        String fromSha = ObjectId.zeroId().name();

        ByteArrayOutputStream packOut = new ByteArrayOutputStream();
        try (PackWriter packWriter = new PackWriter(sourceGit.getRepository())) {
            packWriter.setDeltaBaseAsOffset(false);
            packWriter.preparePack(NullProgressMonitor.INSTANCE, Set.of(ObjectId.fromString(toSha)), Set.of());
            packWriter.writePack(NullProgressMonitor.INSTANCE, NullProgressMonitor.INSTANCE, packOut);
        }
        ByteArrayOutputStream bodyOut = new ByteArrayOutputStream();
        PacketLineOut plo = new PacketLineOut(bodyOut);
        plo.writeString(fromSha + " " + toSha + " refs/heads/main\0 report-status side-band-64k");
        plo.end();
        bodyOut.write(packOut.toByteArray());

        Repository mirror = Git.init()
                .setBare(true)
                .setDirectory(quarantineMirrorDir.toFile())
                .call()
                .getRepository();
        LocalRepositoryCache mockCache = mock(LocalRepositoryCache.class);
        when(mockCache.getOrClone(any(), any(), any(), any())).thenReturn(mirror);

        GitRequestDetails details = makeDetails(fromSha, toSha);
        RequestBodyWrapper request = wrapRequest(bodyOut.toByteArray(), details);

        // The chain stands in for the validation filters that read the pushed objects
        ObjectId[] seenDuringChain = new ObjectId[1];
        FilterChain chain = (req, resp) -> {
            Repository inFlight = details.getLocalRepository();
            try {
                if (inFlight.getObjectDatabase().has(ObjectId.fromString(toSha))) {
                    seenDuringChain[0] = ObjectId.fromString(toSha);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        new EnrichPushCommitsFilter(new GitHubProvider("/proxy"), mockCache)
                .doFilter(request, mock(HttpServletResponse.class), chain);

        assertNotNull(seenDuringChain[0], "Validation filters must be able to read the pushed commit while running");
        assertFalse(details.getPushedCommits().isEmpty(), "The push must still have been inspected");

        try (Repository reopened =
                new FileRepositoryBuilder().setGitDir(mirror.getDirectory()).build()) {
            assertFalse(
                    reopened.getObjectDatabase().has(ObjectId.fromString(toSha)),
                    "The pushed commit must not be left behind in the shared mirror");
        }
        sourceGit.close();
        mirror.close();
    }

    /**
     * Downstream filters get a per-request quarantine rather than the shared mirror itself, so identity is no longer
     * the contract — what they need is a repository backed by the same git directory, seeing the same refs.
     */
    private static void assertSameGitDir(Repository expectedMirror, Repository actual, String message) {
        assertNotNull(actual, message);
        assertEquals(expectedMirror.getDirectory(), actual.getDirectory(), message);
    }
}
