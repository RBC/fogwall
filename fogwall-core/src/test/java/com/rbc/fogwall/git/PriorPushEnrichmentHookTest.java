package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.PushStoreFactory;
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

    private static final String PROVIDER = "github/github.com";

    @TempDir
    Path tempDir;

    Git git;
    Repository repo;
    PushStore pushStore;
    PushContext pushContext;
    ValidationContext validationContext;

    @BeforeEach
    void setUp() throws Exception {
        git = Git.init().setDirectory(tempDir.toFile()).call();
        repo = git.getRepository();
        repo.getConfig().setBoolean("commit", null, "gpgsign", false);
        repo.getConfig().save();
        pushStore = mock(PushStore.class);
        pushContext = new PushContext();
        pushContext.setRepoSlug("/owner/my-repo");
        validationContext = new ValidationContext();
    }

    private PriorPushEnrichmentHook hook(PushStore store) {
        return new PriorPushEnrichmentHook(store, PROVIDER, validationContext, pushContext);
    }

    private static PushRecord forwarded(String provider, String owner, String repoName, String commitTo) {
        return PushRecord.builder()
                .id(UUID.randomUUID().toString())
                .status(PushStatus.FORWARDED)
                .provider(provider)
                .project(owner)
                .repoName(repoName)
                .branch("refs/heads/foo")
                .commitTo(commitTo)
                .build();
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

        hook(pushStore).onPreReceive(rp, List.of(cmd));

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
        when(pushStore.find(argThat(q -> PROVIDER.equals(q.getProvider())
                        && "owner".equals(q.getProject())
                        && "my-repo".equals(q.getRepoName())
                        && "refs/heads/foo".equals(q.getBranch())
                        && PushStatus.FORWARDED == q.getStatus())))
                .thenReturn(List.of(forwarded));

        hook(pushStore).onPreReceive(rp, List.of(cmd));

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

        hook(pushStore).onPreReceive(rp, List.of(cmd));

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

        hook(pushStore).onPreReceive(rp, List.of(cmd));

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

        hook(pushStore).onPreReceive(rp, List.of(cmd));

        assertNull(pushContext.getEffectiveFromId("refs/heads/foo"));
        verifyNoInteractions(pushStore);
    }

    // ---- the lookup is scoped to this repository: provider, owner and name ----

    @Test
    void forwardedPushOfSameNamedRepoUnderAnotherOwner_isNotUsedAsBase() throws Exception {
        RevCommit c1 = createCommit("A");
        RevCommit c2 = createCommit("B");
        PushStore store = PushStoreFactory.h2InMemory("prior-" + UUID.randomUUID());
        store.save(forwarded(
                PROVIDER, "other-org", "my-repo", ObjectId.zeroId().name().replace('0', 'a')));
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/foo", ReceiveCommand.Type.UPDATE);

        hook(store).onPreReceive(new ReceivePack(repo), List.of(cmd));

        assertEquals(
                ObjectId.zeroId().name(),
                pushContext.getEffectiveFromId("refs/heads/foo"),
                "another owner's forward of a same-named repository says nothing about this one");
    }

    @Test
    void forwardedPushOfSameRepoPathOnAnotherProvider_isNotUsedAsBase() throws Exception {
        RevCommit c1 = createCommit("A");
        RevCommit c2 = createCommit("B");
        PushStore store = PushStoreFactory.h2InMemory("prior-" + UUID.randomUUID());
        store.save(forwarded(
                "gitlab/gitlab.com",
                "owner",
                "my-repo",
                ObjectId.zeroId().name().replace('0', 'b')));
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/foo", ReceiveCommand.Type.UPDATE);

        hook(store).onPreReceive(new ReceivePack(repo), List.of(cmd));

        assertEquals(ObjectId.zeroId().name(), pushContext.getEffectiveFromId("refs/heads/foo"));
    }

    @Test
    void forwardedPushOfThisRepo_isUsedAsBase_amongSameNamedOthers() throws Exception {
        RevCommit c1 = createCommit("A");
        RevCommit c2 = createCommit("B");
        RevCommit c3 = createCommit("C");
        PushStore store = PushStoreFactory.h2InMemory("prior-" + UUID.randomUUID());
        store.save(forwarded(PROVIDER, "owner", "my-repo", c1.getId().name()));
        store.save(forwarded(PROVIDER, "other-org", "my-repo", c3.getId().name()));
        store.save(forwarded("gitlab/gitlab.com", "owner", "my-repo", c3.getId().name()));
        ReceiveCommand cmd = new ReceiveCommand(c2.getId(), c3.getId(), "refs/heads/foo", ReceiveCommand.Type.UPDATE);

        hook(store).onPreReceive(new ReceivePack(repo), List.of(cmd));

        assertEquals(c1.getId().name(), pushContext.getEffectiveFromId("refs/heads/foo"));
    }

    @Test
    void nestedOwner_matchesTheWholeNamespace_notASiblingSubgroup() throws Exception {
        RevCommit c1 = createCommit("A");
        RevCommit c2 = createCommit("B");
        RevCommit c3 = createCommit("C");
        pushContext.setRepoSlug("/group/sub-a/my-repo");
        PushStore store = PushStoreFactory.h2InMemory("prior-" + UUID.randomUUID());
        store.save(forwarded(PROVIDER, "group/sub-b", "my-repo", c3.getId().name()));
        store.save(forwarded(PROVIDER, "group/sub-a", "my-repo", c1.getId().name()));
        ReceiveCommand cmd = new ReceiveCommand(c2.getId(), c3.getId(), "refs/heads/foo", ReceiveCommand.Type.UPDATE);

        hook(store).onPreReceive(new ReceivePack(repo), List.of(cmd));

        assertEquals(c1.getId().name(), pushContext.getEffectiveFromId("refs/heads/foo"));
    }

    @Test
    void unparseableRepoPath_blocksThePush_withoutAnyLookup() throws Exception {
        RevCommit c1 = createCommit("A");
        RevCommit c2 = createCommit("B");
        pushContext.setRepoSlug("/just-one-segment");
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/foo", ReceiveCommand.Type.UPDATE);

        hook(pushStore).onPreReceive(new ReceivePack(repo), List.of(cmd));

        assertTrue(validationContext.hasIssues(), "a push this hook cannot scope must not proceed");
        assertNull(pushContext.getEffectiveFromId("refs/heads/foo"));
        verifyNoInteractions(pushStore);
    }

    @Test
    void missingProvider_isRejectedAtConstruction() {
        assertThrows(
                NullPointerException.class,
                () -> new PriorPushEnrichmentHook(pushStore, null, validationContext, pushContext));
    }
}
