package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommitInspectionServiceTest {

    @TempDir
    Path tempDir;

    Git git;
    Repository repo;

    @BeforeEach
    void setUp() throws Exception {
        git = Git.init().setDirectory(tempDir.toFile()).call();
        repo = git.getRepository();
        repo.getConfig().setBoolean("commit", null, "gpgsign", false);
        repo.getConfig().save();
    }

    private RevCommit createCommit(String msg) throws Exception {
        File f = new File(tempDir.toFile(), UUID.randomUUID() + ".txt");
        Files.writeString(f.toPath(), msg);
        git.add().addFilepattern(".").call();
        return git.commit()
                .setAuthor(new PersonIdent("Dev", "dev@example.com"))
                .setCommitter(new PersonIdent("Dev", "dev@example.com"))
                .setMessage(msg)
                .call();
    }

    // ---- getCommitRangeUpTo ----

    @Test
    void getCommitRangeUpTo_singleCommit_returnsIt() throws Exception {
        RevCommit c1 = createCommit("init");

        List<Commit> commits =
                CommitInspectionService.getCommitRangeUpTo(repo, c1.getId().name());

        assertEquals(1, commits.size());
        assertEquals(c1.getId().name(), commits.get(0).getSha());
    }

    @Test
    void getCommitRangeUpTo_multipleCommits_returnsAllAncestors() throws Exception {
        RevCommit c1 = createCommit("A");
        RevCommit c2 = createCommit("B");
        RevCommit c3 = createCommit("C");

        List<Commit> commits =
                CommitInspectionService.getCommitRangeUpTo(repo, c3.getId().name());

        assertEquals(3, commits.size());
        // JGit log returns newest-first
        assertEquals(c3.getId().name(), commits.get(0).getSha());
        assertEquals(c2.getId().name(), commits.get(1).getSha());
        assertEquals(c1.getId().name(), commits.get(2).getSha());
    }

    @Test
    void getCommitRangeUpTo_includesCommitsAlreadyInLocalRefs() throws Exception {
        // This is the key property: unlike getCommitRange with zero fromCommit,
        // getCommitRangeUpTo does NOT exclude commits reachable from existing refs.
        RevCommit c1 = createCommit("A");
        RevCommit c2 = createCommit("B");

        // Simulate local ref pointing at c1 (as happens when a push was approved but not forwarded)
        git.branchCreate().setName("feature").setStartPoint(c1).call();

        // getCommitRangeUpTo from c2 must include c1 even though it's in a local ref
        List<Commit> commits =
                CommitInspectionService.getCommitRangeUpTo(repo, c2.getId().name());

        assertEquals(2, commits.size(), "must include c1 even though it's reachable from a local ref");
    }

    // ---- getAnnotatedTagMessage (#474) ----

    @Test
    void getAnnotatedTagMessage_annotatedTag_returnsMessage() throws Exception {
        createCommit("init");
        var ref = git.tag()
                .setName("v1.0.0")
                .setMessage("Release 1.0.0 notes")
                .setAnnotated(true)
                .call();

        var message = CommitInspectionService.getAnnotatedTagMessage(repo, ref.getObjectId());

        assertTrue(message.isPresent());
        assertTrue(message.get().contains("Release 1.0.0 notes"));
    }

    @Test
    void getAnnotatedTagMessage_lightweightTag_returnsEmpty() throws Exception {
        createCommit("init");
        // A lightweight tag's ref points straight at the commit — there is no tag object or message.
        var ref = git.tag().setName("v1.0.0").setAnnotated(false).call();

        var message = CommitInspectionService.getAnnotatedTagMessage(repo, ref.getObjectId());

        assertTrue(message.isEmpty());
    }

    @Test
    void getAnnotatedTagMessage_commitObject_returnsEmpty() throws Exception {
        RevCommit c = createCommit("a commit, not a tag");

        var message = CommitInspectionService.getAnnotatedTagMessage(repo, c.getId());

        assertTrue(message.isEmpty());
    }

    @Test
    void getAnnotatedTagTagger_annotatedTag_returnsTagger() throws Exception {
        createCommit("init");
        var ref = git.tag()
                .setName("v1.0.0")
                .setMessage("Release 1.0.0")
                .setTagger(new PersonIdent("Tag Ger", "tagger@example.com"))
                .setAnnotated(true)
                .call();

        var tagger = CommitInspectionService.getAnnotatedTagTagger(repo, ref.getObjectId());

        assertTrue(tagger.isPresent());
        assertEquals("Tag Ger", tagger.get().getName());
        assertEquals("tagger@example.com", tagger.get().getEmail());
    }

    @Test
    void getAnnotatedTagTagger_lightweightTag_returnsEmpty() throws Exception {
        createCommit("init");
        var ref = git.tag().setName("v1.0.0").setAnnotated(false).call();

        assertTrue(CommitInspectionService.getAnnotatedTagTagger(repo, ref.getObjectId())
                .isEmpty());
    }

    @Test
    void getAnnotatedTagTagger_commitObject_returnsEmpty() throws Exception {
        RevCommit c = createCommit("a commit, not a tag");

        assertTrue(
                CommitInspectionService.getAnnotatedTagTagger(repo, c.getId()).isEmpty());
    }

    @Test
    void getAnnotatedTagTagger_nullId_returnsEmpty() throws Exception {
        assertTrue(CommitInspectionService.getAnnotatedTagTagger(repo, null).isEmpty());
    }

    @Test
    void getAnnotatedTagMessage_nullId_returnsEmpty() throws Exception {
        assertTrue(CommitInspectionService.getAnnotatedTagMessage(repo, null).isEmpty());
    }

    // --- trailer parsing (#146) ---

    @Test
    void parseTrailer_extractsSignedOffByAndCoAuthoredBy_inOrder_caseInsensitive() {
        String message = """
                Add a feature

                Body text mentioning signed-off-by inline should be ignored unless it starts the line.

                Signed-off-by: Dev One <dev1@corp.com>
                co-authored-by: Pair Two <pair2@corp.com>
                Co-Authored-By: Bot <noreply@anthropic.com>
                """;

        assertEquals(
                List.of("Dev One <dev1@corp.com>"), CommitInspectionService.parseTrailer(message, "Signed-off-by:"));
        assertEquals(
                List.of("Pair Two <pair2@corp.com>", "Bot <noreply@anthropic.com>"),
                CommitInspectionService.parseTrailer(message, "Co-authored-by:"));
    }

    @Test
    void parseTrailer_noMatches_returnsEmpty() {
        assertTrue(CommitInspectionService.parseTrailer("just a subject", "Co-authored-by:")
                .isEmpty());
        assertTrue(CommitInspectionService.parseTrailer(null, "Signed-off-by:").isEmpty());
    }
}
