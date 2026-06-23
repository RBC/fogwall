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
}
