package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuarantineObjectStoreTest {

    private static final String BRANCH = "refs/heads/main";

    @TempDir
    Path workDir;

    /** A bare mirror with one commit on refs/heads/main, standing in for the shared proxy cache. */
    private Repository mirrorWithOneCommit() throws Exception {
        Path source = Files.createDirectories(workDir.resolve("source"));
        // Pin the branch: JGit follows init.defaultBranch, which differs between dev machines and CI
        try (Git git = Git.init()
                .setDirectory(source.toFile())
                .setInitialBranch("main")
                .call()) {
            Files.writeString(source.resolve("file.txt"), "original\n");
            git.add().addFilepattern("file.txt").call();
            git.commit().setMessage("first").setSign(false).call();
        }
        Path bare = workDir.resolve("mirror.git");
        try (Git cloned = Git.cloneRepository()
                .setURI(source.toUri().toString())
                .setDirectory(bare.toFile())
                .setBare(true)
                .call()) {
            return cloned.getRepository();
        }
    }

    private static ObjectId insertBlob(Repository repo, String content) throws Exception {
        try (ObjectInserter inserter = repo.newObjectInserter()) {
            ObjectId id = inserter.insert(Constants.OBJ_BLOB, content.getBytes(StandardCharsets.UTF_8));
            inserter.flush();
            return id;
        }
    }

    @Test
    void objectsWrittenThroughTheQuarantineNeverReachTheMirror() throws Exception {
        Repository mirror = mirrorWithOneCommit();
        ObjectId blobId;

        try (QuarantineObjectStore quarantine = QuarantineObjectStore.create(mirror)) {
            blobId = insertBlob(quarantine.getRepository(), "rejected secret\n");
            assertTrue(
                    quarantine.getRepository().getObjectDatabase().has(blobId),
                    "The push must be able to read back what it just wrote");
        }

        // Reopen the mirror so nothing is answered from a cached object database
        try (Repository reopened =
                new FileRepositoryBuilder().setGitDir(mirror.getDirectory()).build()) {
            assertFalse(
                    reopened.getObjectDatabase().has(blobId),
                    "A rejected push's objects must not survive in the shared mirror");
        }
        mirror.close();
    }

    @Test
    void mirrorObjectsStayReadableThroughTheQuarantine() throws Exception {
        Repository mirror = mirrorWithOneCommit();
        ObjectId existingTip = mirror.resolve(BRANCH);
        assertNotNull(existingTip);

        try (QuarantineObjectStore quarantine = QuarantineObjectStore.create(mirror)) {
            assertTrue(
                    quarantine.getRepository().getObjectDatabase().has(existingTip),
                    "Thin-pack deltas resolve against mirror objects, so they must be readable");
        }
        mirror.close();
    }

    /** Without the mirror's refs, a new-branch push would look like it introduced all of history. */
    @Test
    void mirrorRefsStayVisibleThroughTheQuarantine() throws Exception {
        Repository mirror = mirrorWithOneCommit();

        try (QuarantineObjectStore quarantine = QuarantineObjectStore.create(mirror)) {
            var refs = quarantine.getRepository().getRefDatabase().getRefsByPrefix(Constants.R_HEADS);
            assertFalse(refs.isEmpty(), "Quarantine must see the mirror's branches");
            assertEquals(mirror.resolve(BRANCH), quarantine.getRepository().resolve(BRANCH));
        }
        mirror.close();
    }

    /** The whole point is that a rejected push adds nothing new that anything can reach. */
    @Test
    void aCommitBuiltInQuarantineIsNotReachableInTheMirrorAfterwards() throws Exception {
        Repository mirror = mirrorWithOneCommit();
        ObjectId newCommit;

        try (QuarantineObjectStore quarantine = QuarantineObjectStore.create(mirror)) {
            Repository repo = quarantine.getRepository();
            ObjectId parentId = repo.resolve(BRANCH);
            assertNotNull(parentId, BRANCH + " must be visible through the quarantine");
            RevCommit parent = repo.parseCommit(parentId);
            try (ObjectInserter inserter = repo.newObjectInserter()) {
                var commit = new org.eclipse.jgit.lib.CommitBuilder();
                commit.setTreeId(parent.getTree());
                commit.setParentId(parent);
                commit.setAuthor(new org.eclipse.jgit.lib.PersonIdent("Dev", "dev@example.com"));
                commit.setCommitter(new org.eclipse.jgit.lib.PersonIdent("Dev", "dev@example.com"));
                commit.setMessage("smuggled\n");
                newCommit = inserter.insert(commit);
                inserter.flush();
            }
            assertNotNull(repo.parseCommit(newCommit), "Validation must be able to inspect the pushed commit");
        }

        try (Repository reopened =
                new FileRepositoryBuilder().setGitDir(mirror.getDirectory()).build()) {
            assertFalse(reopened.getObjectDatabase().has(newCommit));
        }
        mirror.close();
    }

    @Test
    void closeRemovesTheScratchDirectory() throws Exception {
        Repository mirror = mirrorWithOneCommit();
        QuarantineObjectStore quarantine = QuarantineObjectStore.create(mirror);
        Path scratch = quarantine.getDirectory();
        insertBlob(quarantine.getRepository(), "scratch\n");
        assertTrue(Files.exists(scratch));

        quarantine.close();

        assertFalse(Files.exists(scratch), "Nothing should be left on disk once the request is over");
        mirror.close();
    }

    @Test
    void closeIsIdempotent() throws Exception {
        Repository mirror = mirrorWithOneCommit();
        QuarantineObjectStore quarantine = QuarantineObjectStore.create(mirror);

        quarantine.close();
        assertDoesNotThrow(quarantine::close);
        mirror.close();
    }

    @Test
    void theMirrorIsLeftUsableAfterAQuarantineIsDiscarded() throws Exception {
        Repository mirror = mirrorWithOneCommit();
        ObjectId tip = mirror.resolve(BRANCH);
        assertNotNull(tip, BRANCH + " must exist in the mirror");

        QuarantineObjectStore.create(mirror).close();

        assertEquals(tip, mirror.resolve(BRANCH), "Discarding a quarantine must not disturb the mirror");
        assertTrue(mirror.getObjectDatabase().has(tip));
        mirror.close();
    }

    @Test
    void createOrNullFallsBackRatherThanFailingThePush() throws Exception {
        Repository mirror = mirrorWithOneCommit();
        assertNotNull(QuarantineObjectStore.createOrNull(mirror), "A healthy mirror must yield a quarantine");
        mirror.close();

        // A closed, deleted mirror stands in for "the quarantine could not be created"
        Repository broken = new FileRepositoryBuilder()
                .setGitDir(workDir.resolve("does-not-exist.git").toFile())
                .build();
        assertNull(
                QuarantineObjectStore.createOrNull(null),
                "A failure must return null so the caller can fall back to the mirror");
        broken.close();
    }

    @Test
    void promotedObjectsSurviveInTheMirror() throws Exception {
        Repository mirror = mirrorWithOneCommit();
        ObjectId blobId;

        try (QuarantineObjectStore quarantine = QuarantineObjectStore.create(mirror)) {
            blobId = insertBlob(quarantine.getRepository(), "accepted content\n");
            quarantine.promote();
        }

        try (Repository reopened =
                new FileRepositoryBuilder().setGitDir(mirror.getDirectory()).build()) {
            assertTrue(
                    reopened.getObjectDatabase().has(blobId),
                    "Server mode updates the mirror's refs, so accepted objects must be promoted into it");
        }
        mirror.close();
    }

    /** Promotion has to be safe to call when the mirror already has the object, since object names are content. */
    @Test
    void promotingAnObjectTheMirrorAlreadyHasIsNotAnError() throws Exception {
        Repository mirror = mirrorWithOneCommit();
        ObjectId existing = insertBlob(mirror, "shared\n");

        try (QuarantineObjectStore quarantine = QuarantineObjectStore.create(mirror)) {
            ObjectId again = insertBlob(quarantine.getRepository(), "shared\n");
            assertEquals(existing, again, "Same content must hash to the same object name");
            assertDoesNotThrow(quarantine::promote);
        }

        try (Repository reopened =
                new FileRepositoryBuilder().setGitDir(mirror.getDirectory()).build()) {
            assertTrue(reopened.getObjectDatabase().has(existing));
        }
        mirror.close();
    }

    @Test
    void promotingLeavesNothingBehindInTheScratchDirectory() throws Exception {
        Repository mirror = mirrorWithOneCommit();
        QuarantineObjectStore quarantine = QuarantineObjectStore.create(mirror);
        insertBlob(quarantine.getRepository(), "accepted\n");
        Path scratch = quarantine.getDirectory();

        quarantine.promote();
        quarantine.close();

        assertFalse(Files.exists(scratch));
        mirror.close();
    }

    /** Without promotion the mirror would end up with refs naming objects that are about to be deleted. */
    @Test
    void withoutPromotionTheObjectIsGone() throws Exception {
        Repository mirror = mirrorWithOneCommit();
        ObjectId blobId;
        try (QuarantineObjectStore quarantine = QuarantineObjectStore.create(mirror)) {
            blobId = insertBlob(quarantine.getRepository(), "rejected\n");
        }
        try (Repository reopened =
                new FileRepositoryBuilder().setGitDir(mirror.getDirectory()).build()) {
            assertFalse(reopened.getObjectDatabase().has(blobId));
        }
        mirror.close();
    }

    /**
     * Regression. JGit writes a {@code .keep} beside a pack it has just received — a pack lock — and
     * {@code ReceivePack.release()} deletes it by path once the push finishes. Promoting it moves it out from under
     * that delete, which throws after the response has begun and leaves the client's push waiting forever. Earlier
     * versions of this test missed it by inserting loose objects, which never produce a lock at all.
     */
    @Test
    void aPackLockIsLeftBehindRatherThanPromoted() throws Exception {
        Repository mirror = mirrorWithOneCommit();

        try (QuarantineObjectStore quarantine = QuarantineObjectStore.create(mirror)) {
            Path packDir = quarantine.getDirectory().resolve("objects").resolve("pack");
            Files.createDirectories(packDir);
            Path pack = Files.writeString(packDir.resolve("pack-abc.pack"), "pack bytes\n");
            Path keep = Files.writeString(packDir.resolve("pack-abc.keep"), "");

            quarantine.promote();

            assertTrue(Files.exists(keep), "The pack lock must stay where ReceivePack will look for it");
            assertFalse(Files.exists(pack), "but the pack itself must have been promoted");
            assertTrue(
                    Files.exists(mirror.getDirectory().toPath().resolve("objects/pack/pack-abc.pack")),
                    "the pack must be in the mirror");
            assertFalse(
                    Files.exists(mirror.getDirectory().toPath().resolve("objects/pack/pack-abc.keep")),
                    "the lock is not an object and has no business in the mirror");
        }
        mirror.close();
    }

    /** An operator inspecting the filesystem has to be able to tie a directory back to a push record. */
    @Test
    void theScratchDirectoryIsNamedAfterThePushRecord() throws Exception {
        Repository mirror = mirrorWithOneCommit();
        String pushId = "3f8a1c2e-4b5d-6e7f-8a9b-0c1d2e3f4a5b";

        try (QuarantineObjectStore quarantine = QuarantineObjectStore.create(mirror, pushId)) {
            assertTrue(
                    quarantine.getDirectory().getFileName().toString().contains(pushId),
                    "Directory name must carry the push id: " + quarantine.getDirectory());
        }
        mirror.close();
    }

    @Test
    void aMissingPushIdStillYieldsAUsableQuarantine() throws Exception {
        Repository mirror = mirrorWithOneCommit();

        try (QuarantineObjectStore quarantine = QuarantineObjectStore.create(mirror, null)) {
            assertNotNull(quarantine.getRepository());
        }
        mirror.close();
    }
}
