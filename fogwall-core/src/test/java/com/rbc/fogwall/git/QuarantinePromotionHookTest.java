package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuarantinePromotionHookTest {

    @TempDir
    Path workDir;

    private Repository bareMirror() throws Exception {
        return Git.init()
                .setBare(true)
                .setDirectory(workDir.resolve("mirror.git").toFile())
                .call()
                .getRepository();
    }

    private static ObjectId insertBlob(Repository repo, String content) throws Exception {
        try (ObjectInserter inserter = repo.newObjectInserter()) {
            ObjectId id = inserter.insert(Constants.OBJ_BLOB, content.getBytes(StandardCharsets.UTF_8));
            inserter.flush();
            return id;
        }
    }

    private static ReceiveCommand command() {
        return new ReceiveCommand(
                ObjectId.zeroId(), ObjectId.fromString("0123456789012345678901234567890123456789"), "refs/heads/main");
    }

    @Test
    void promotesWhenNothingWasRejected() throws Exception {
        Repository mirror = bareMirror();
        ObjectId blobId;
        try (QuarantineObjectStore quarantine = QuarantineObjectStore.create(mirror)) {
            blobId = insertBlob(quarantine.getRepository(), "accepted\n");

            ReceiveCommand cmd = command();
            new QuarantinePromotionHook(quarantine).onPreReceive(mock(ReceivePack.class), List.of(cmd));

            assertEquals(ReceiveCommand.Result.NOT_ATTEMPTED, cmd.getResult(), "Promotion must not touch the verdict");
        }
        try (Repository reopened =
                new FileRepositoryBuilder().setGitDir(mirror.getDirectory()).build()) {
            assertTrue(reopened.getObjectDatabase().has(blobId));
        }
        mirror.close();
    }

    @Test
    void doesNotPromoteWhenEveryCommandWasRejected() throws Exception {
        Repository mirror = bareMirror();
        ObjectId blobId;
        try (QuarantineObjectStore quarantine = QuarantineObjectStore.create(mirror)) {
            blobId = insertBlob(quarantine.getRepository(), "rejected\n");

            ReceiveCommand cmd = command();
            cmd.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "blocked by policy");
            new QuarantinePromotionHook(quarantine).onPreReceive(mock(ReceivePack.class), List.of(cmd));
        }
        try (Repository reopened =
                new FileRepositoryBuilder().setGitDir(mirror.getDirectory()).build()) {
            assertFalse(
                    reopened.getObjectDatabase().has(blobId),
                    "A rejected push's objects are exactly what must not reach the mirror");
        }
        mirror.close();
    }

    @Test
    void promotionFailureRejectsTheOutstandingCommands() throws Exception {
        Repository mirror = bareMirror();
        QuarantineObjectStore quarantine = QuarantineObjectStore.create(mirror);
        insertBlob(quarantine.getRepository(), "accepted\n");

        // Make the mirror's object directory unusable as a move target
        Path mirrorObjects = mirror.getDirectory().toPath().resolve(Constants.OBJECTS);
        try (var walk = Files.walk(mirrorObjects)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> p.toFile().delete());
        }
        Files.createFile(mirrorObjects);

        ReceiveCommand cmd = command();
        new QuarantinePromotionHook(quarantine).onPreReceive(mock(ReceivePack.class), List.of(cmd));

        assertEquals(
                ReceiveCommand.Result.REJECTED_OTHER_REASON,
                cmd.getResult(),
                "A half-promoted push would leave the mirror naming objects about to be deleted");
        quarantine.close();
        mirror.close();
    }
}
