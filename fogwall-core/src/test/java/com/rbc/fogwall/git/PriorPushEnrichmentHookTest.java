package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PriorPushEnrichmentHookTest {

    @TempDir
    Path tempDir;

    Git git;
    Repository repo;
    PushStore pushStore;
    PushContext pushContext;

    @BeforeEach
    void setUp() throws Exception {
        git = Git.init().setDirectory(tempDir.toFile()).call();
        repo = git.getRepository();
        repo.getConfig().setBoolean("commit", null, "gpgsign", false);
        repo.getConfig().save();
        pushStore = mock(PushStore.class);
        pushContext = new PushContext();
        pushContext.setRepoSlug("/owner/my-repo");
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

    // ---- truly new branch (oldId = zero) → no enrichment ----

    @Test
    void trulyNewBranch_noEnrichment() throws Exception {
        RevCommit c = createCommit("init");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd =
                new ReceiveCommand(ObjectId.zeroId(), c.getId(), "refs/heads/foo", ReceiveCommand.Type.CREATE);

        new PriorPushEnrichmentHook(pushStore, pushContext).onPreReceive(rp, List.of(cmd));

        assertNull(pushContext.getEffectiveFromId("refs/heads/foo"));
        verifyNoInteractions(pushStore);
    }

    // ---- non-zero oldId that was forwarded → no enrichment ----

    @Test
    void repushAfterForward_upstreamInSync_noEnrichment() throws Exception {
        RevCommit c1 = createCommit("init");
        RevCommit c2 = createCommit("second");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/foo", ReceiveCommand.Type.UPDATE);

        PushRecord forwarded = PushRecord.builder()
                .id(UUID.randomUUID().toString())
                .status(PushStatus.FORWARDED)
                .branch("refs/heads/foo")
                .commitTo(c1.getId().name()) // upstream has c1 (matches cmd.getOldId())
                .build();
        when(pushStore.find(argThat(q -> "my-repo".equals(q.getRepoName())
                        && "refs/heads/foo".equals(q.getBranch())
                        && PushStatus.FORWARDED == q.getStatus())))
                .thenReturn(List.of(forwarded));

        new PriorPushEnrichmentHook(pushStore, pushContext).onPreReceive(rp, List.of(cmd));

        assertNull(
                pushContext.getEffectiveFromId("refs/heads/foo"),
                "No enrichment needed — upstream is in sync with local cache tip");
    }

    // ---- non-zero oldId, nothing forwarded → effective base is zero ----

    @Test
    void repushAfterCancel_nothingForwarded_effectiveBaseIsZero() throws Exception {
        RevCommit c1 = createCommit("init");
        RevCommit c2 = createCommit("second");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/foo", ReceiveCommand.Type.UPDATE);

        when(pushStore.find(any())).thenReturn(List.of()); // no forwarded pushes

        new PriorPushEnrichmentHook(pushStore, pushContext).onPreReceive(rp, List.of(cmd));

        String effectiveFrom = pushContext.getEffectiveFromId("refs/heads/foo");
        assertNotNull(effectiveFrom, "Enrichment should be triggered when nothing was forwarded");
        assertTrue(effectiveFrom.matches("^0+$"), "Effective base should be the zero OID");
    }

    // ---- non-zero oldId, some commits forwarded but not the current tip → effective base is last forwarded ----

    @Test
    void repushAfterPartialForward_effectiveBaseIsLastForwardedSha() throws Exception {
        RevCommit c1 = createCommit("A");
        RevCommit c2 = createCommit("B");
        RevCommit c3 = createCommit("C");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c2.getId(), c3.getId(), "refs/heads/foo", ReceiveCommand.Type.UPDATE);

        // Upstream has c1 (A was forwarded), but c2 (B) was cached locally and then re-pushed
        PushRecord forwarded = PushRecord.builder()
                .id(UUID.randomUUID().toString())
                .status(PushStatus.FORWARDED)
                .branch("refs/heads/foo")
                .commitTo(c1.getId().name())
                .build();
        when(pushStore.find(any())).thenReturn(List.of(forwarded));

        new PriorPushEnrichmentHook(pushStore, pushContext).onPreReceive(rp, List.of(cmd));

        assertEquals(
                c1.getId().name(),
                pushContext.getEffectiveFromId("refs/heads/foo"),
                "Effective base should be the last forwarded SHA (c1)");
    }

    // ---- DELETE command → skipped ----

    @Test
    void deleteCommand_skipped() throws Exception {
        RevCommit c1 = createCommit("init");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd =
                new ReceiveCommand(c1.getId(), ObjectId.zeroId(), "refs/heads/foo", ReceiveCommand.Type.DELETE);

        new PriorPushEnrichmentHook(pushStore, pushContext).onPreReceive(rp, List.of(cmd));

        assertNull(pushContext.getEffectiveFromId("refs/heads/foo"));
        verifyNoInteractions(pushStore);
    }
}
