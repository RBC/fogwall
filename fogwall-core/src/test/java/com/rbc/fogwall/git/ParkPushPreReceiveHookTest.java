package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.ParkedPushStore;
import com.rbc.fogwall.db.model.ParkedRefUpdate;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A push is only acknowledged if it can be forwarded later, so every way parking can fail must reject the push rather
 * than let the chain go on to acknowledge it.
 */
class ParkPushPreReceiveHookTest {

    private static final String PUSH_ID = "push-1";
    private static final String UPSTREAM = "https://github.com/acme/repo.git";

    @TempDir
    Path tempDir;

    private final ParkedPushStore store = mock(ParkedPushStore.class);
    private final ValidationContext validationContext = new ValidationContext();
    private final PushContext pushContext = new PushContext();
    private Repository repo;
    private QuarantineObjectStore quarantine;
    private ReceivePack rp;
    private ReceiveCommand cmd;

    @BeforeEach
    void setUp() throws Exception {
        repo = Git.init().setBare(true).setDirectory(tempDir.toFile()).call().getRepository();
        quarantine = QuarantineObjectStore.create(repo, PUSH_ID);
        rp = new ReceivePack(quarantine.getRepository());
        cmd = new ReceiveCommand(
                ObjectId.zeroId(), ObjectId.fromString("1234567890123456789012345678901234567890"), "refs/heads/b");
        pushContext.setPushId(PUSH_ID);
        pushContext.setUpstreamUrl(UPSTREAM);
    }

    @AfterEach
    void tearDown() {
        quarantine.close();
    }

    private ParkPushPreReceiveHook hook(QuarantineObjectStore quarantine) {
        return new ParkPushPreReceiveHook(store, quarantine, validationContext, pushContext, "github", "alice");
    }

    private void writePack(String name, byte[] content) throws IOException {
        Path packDir = Files.createDirectories(quarantine.getObjectsDirectory().resolve("pack"));
        Files.write(packDir.resolve(name), content);
    }

    @Test
    void storesTheQuarantinePackAndRefUpdates() throws Exception {
        writePack("pack-abc.pack", new byte[] {1, 2, 3});
        List<ParkedRefUpdate> refs = List.of(new ParkedRefUpdate(
                "refs/heads/b",
                ObjectId.zeroId().name(),
                "1234567890123456789012345678901234567890",
                ReceiveCommand.Type.CREATE));
        when(store.park(eq(PUSH_ID), eq("github"), eq("alice"), eq(UPSTREAM), eq(refs), any()))
                .thenAnswer(inv -> {
                    assertArrayEquals(
                            new byte[] {1, 2, 3},
                            inv.getArgument(5, InputStream.class).readAllBytes());
                    return null;
                });

        hook(quarantine).onPreReceive(rp, List.of(cmd));

        verify(store).park(eq(PUSH_ID), eq("github"), eq("alice"), eq(UPSTREAM), eq(refs), any());
        assertEquals(ReceiveCommand.Result.NOT_ATTEMPTED, cmd.getResult());
    }

    @Test
    void noObjectsPushed_storesAnEmptyPack() throws Exception {
        when(store.park(anyString(), anyString(), anyString(), anyString(), anyList(), any()))
                .thenAnswer(inv -> {
                    assertEquals(-1, inv.getArgument(5, InputStream.class).read());
                    return null;
                });

        hook(quarantine).onPreReceive(rp, List.of(cmd));

        verify(store).park(anyString(), anyString(), anyString(), anyString(), anyList(), any());
        assertEquals(ReceiveCommand.Result.NOT_ATTEMPTED, cmd.getResult());
    }

    @Test
    void validationIssues_leftForThePersistenceHookToReject() {
        validationContext.addIssue(PushStepKind.EMPTY_BRANCH, "blocked", "blocked");

        hook(quarantine).onPreReceive(rp, List.of(cmd));

        verifyNoInteractions(store);
        assertEquals(ReceiveCommand.Result.NOT_ATTEMPTED, cmd.getResult());
    }

    @Test
    void noQuarantine_rejects() {
        hook(null).onPreReceive(rp, List.of(cmd));

        verifyNoInteractions(store);
        assertEquals(ReceiveCommand.Result.REJECTED_OTHER_REASON, cmd.getResult());
    }

    @Test
    void noUpstreamUrl_rejects() {
        pushContext.setUpstreamUrl(null);

        hook(quarantine).onPreReceive(rp, List.of(cmd));

        verifyNoInteractions(store);
        assertEquals(ReceiveCommand.Result.REJECTED_OTHER_REASON, cmd.getResult());
    }

    @Test
    void storeFailure_rejects() throws Exception {
        when(store.park(anyString(), anyString(), anyString(), anyString(), anyList(), any()))
                .thenThrow(new IllegalStateException("database down"));

        hook(quarantine).onPreReceive(rp, List.of(cmd));

        assertEquals(ReceiveCommand.Result.REJECTED_OTHER_REASON, cmd.getResult());
    }

    @Test
    void moreThanOnePack_rejects() throws Exception {
        writePack("pack-a.pack", new byte[] {1});
        writePack("pack-b.pack", new byte[] {2});

        hook(quarantine).onPreReceive(rp, List.of(cmd));

        verifyNoInteractions(store);
        assertEquals(ReceiveCommand.Result.REJECTED_OTHER_REASON, cmd.getResult());
    }
}
