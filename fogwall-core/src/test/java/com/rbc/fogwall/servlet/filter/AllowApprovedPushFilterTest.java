package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;
import static com.rbc.fogwall.servlet.FogwallServlet.PRE_APPROVED_ATTR;
import static com.rbc.fogwall.servlet.FogwallServlet.SERVICE_URL_ATTR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.PushStoreFactory;
import com.rbc.fogwall.db.model.Attestation;
import com.rbc.fogwall.db.model.PushQuery;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.git.Commit;
import com.rbc.fogwall.git.Contributor;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.GitHubProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AllowApprovedPushFilterTest {

    private static HttpServletRequest mockPushRequest(GitRequestDetails details) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContentType()).thenReturn("application/x-git-receive-pack-request");
        when(req.getRequestURI()).thenReturn("/proxy/github.com/owner/repo.git/git-receive-pack");
        when(req.getAttribute(GIT_REQUEST_ATTR)).thenReturn(details);
        when(req.getHeaderNames()).thenReturn(java.util.Collections.emptyEnumeration());
        return req;
    }

    private static GitRequestDetails pushDetailsFor(String commitTo, String branch, String repoName) {
        return pushDetailsFor(commitTo, branch, repoName, "owner", "github");
    }

    private static GitRequestDetails pushDetailsFor(
            String commitTo, String branch, String repoName, String owner, String providerName) {
        GitRequestDetails details = new GitRequestDetails();
        details.setCommitTo(commitTo);
        details.setBranch(branch);
        details.setProvider(GitHubProvider.builder().name(providerName).build());
        details.setRepoRef(GitRequestDetails.RepoRef.builder()
                .owner(owner)
                .name(repoName)
                .slug("/" + owner + "/" + repoName)
                .build());
        details.getPushedCommits()
                .add(Commit.builder()
                        .sha(commitTo)
                        .author(Contributor.builder()
                                .name("Dev")
                                .email("dev@example.com")
                                .build())
                        .committer(Contributor.builder()
                                .name("Dev")
                                .email("dev@example.com")
                                .build())
                        .message("test")
                        .build());
        return details;
    }

    @Test
    void noApprovedRecord_doesNotSetPreApproved() throws Exception {
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        AllowApprovedPushFilter filter = new AllowApprovedPushFilter(store, "http://localhost:8080", null);
        GitRequestDetails details = pushDetailsFor("abc123", "refs/heads/main", "my-repo");
        HttpServletRequest req = mockPushRequest(details);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filter.doHttpFilter(req, resp);

        verify(req, never()).setAttribute(eq(PRE_APPROVED_ATTR), any());
    }

    @Test
    void approvedRecord_setsPreApproved() throws Exception {
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        // Save an approved push record for the same commitTo + branch + repo
        PushRecord approved = PushRecord.builder()
                .commitTo("deadbeef")
                .branch("refs/heads/main")
                .provider("github")
                .project("owner")
                .repoName("my-repo")
                .build();
        store.save(approved);
        store.approve(
                approved.getId(),
                Attestation.builder()
                        .pushId(approved.getId())
                        .type(Attestation.Type.APPROVAL)
                        .reviewerUsername("admin")
                        .build());

        AllowApprovedPushFilter filter = new AllowApprovedPushFilter(store, "http://localhost:8080", null);
        GitRequestDetails details = pushDetailsFor("deadbeef", "refs/heads/main", "my-repo");
        HttpServletRequest req = mockPushRequest(details);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filter.doHttpFilter(req, resp);

        verify(req).setAttribute(PRE_APPROVED_ATTR, Boolean.TRUE);
    }

    @Test
    void selfApprovedWithoutSelfCertify_doesNotSetPreApproved() throws Exception {
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        PushRecord approved = PushRecord.builder()
                .commitTo("deadbeef")
                .branch("refs/heads/main")
                .provider("github")
                .project("owner")
                .repoName("my-repo")
                .url("github.com/owner/my-repo.git")
                .resolvedUser("alice")
                .build();
        store.save(approved);
        // alice approved her own push — a self-approval.
        store.approve(
                approved.getId(),
                Attestation.builder()
                        .pushId(approved.getId())
                        .type(Attestation.Type.APPROVAL)
                        .reviewerUsername("alice")
                        .build());

        RepoPermissionService permissionService = mock(RepoPermissionService.class);
        when(permissionService.isBypassReviewAllowed("alice", "github", "github.com/owner/my-repo.git"))
                .thenReturn(false);

        AllowApprovedPushFilter filter = new AllowApprovedPushFilter(store, "http://localhost:8080", permissionService);
        GitRequestDetails details = pushDetailsFor("deadbeef", "refs/heads/main", "my-repo");
        HttpServletRequest req = mockPushRequest(details);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filter.doHttpFilter(req, resp);

        // The prior self-approval carries no SELF_CERTIFY permission — it must not be honored on re-push.
        verify(req, never()).setAttribute(eq(PRE_APPROVED_ATTR), any());
        // And it must not linger as APPROVED: the stale record is demoted to ERROR so it can never forward.
        assertEquals(
                PushStatus.ERROR, store.findById(approved.getId()).orElseThrow().getStatus());
    }

    @Test
    void selfApprovedWithSelfCertify_setsPreApproved() throws Exception {
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        PushRecord approved = PushRecord.builder()
                .commitTo("deadbeef")
                .branch("refs/heads/main")
                .provider("github")
                .project("owner")
                .repoName("my-repo")
                .url("github.com/owner/my-repo.git")
                .resolvedUser("alice")
                .build();
        store.save(approved);
        store.approve(
                approved.getId(),
                Attestation.builder()
                        .pushId(approved.getId())
                        .type(Attestation.Type.APPROVAL)
                        .reviewerUsername("alice")
                        .build());

        RepoPermissionService permissionService = mock(RepoPermissionService.class);
        when(permissionService.isBypassReviewAllowed("alice", "github", "github.com/owner/my-repo.git"))
                .thenReturn(true);

        AllowApprovedPushFilter filter = new AllowApprovedPushFilter(store, "http://localhost:8080", permissionService);
        GitRequestDetails details = pushDetailsFor("deadbeef", "refs/heads/main", "my-repo");
        HttpServletRequest req = mockPushRequest(details);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filter.doHttpFilter(req, resp);

        verify(req).setAttribute(PRE_APPROVED_ATTR, Boolean.TRUE);
    }

    @Test
    void alwaysSetsServiceUrlAttribute() throws Exception {
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        AllowApprovedPushFilter filter = new AllowApprovedPushFilter(store, "http://my-dashboard:8080", null);
        GitRequestDetails details = pushDetailsFor("abc", "refs/heads/main", "repo");
        HttpServletRequest req = mockPushRequest(details);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filter.doHttpFilter(req, resp);

        verify(req).setAttribute(SERVICE_URL_ATTR, "http://my-dashboard:8080");
    }

    @Test
    void approvedRecordForDifferentCommit_doesNotSetPreApproved() throws Exception {
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        PushRecord approved = PushRecord.builder()
                .commitTo("aaaaaa")
                .branch("refs/heads/main")
                .provider("github")
                .project("owner")
                .repoName("repo")
                .build();
        store.save(approved);
        store.approve(
                approved.getId(),
                Attestation.builder()
                        .pushId(approved.getId())
                        .type(Attestation.Type.APPROVAL)
                        .reviewerUsername("admin")
                        .build());

        AllowApprovedPushFilter filter = new AllowApprovedPushFilter(store, "http://localhost:8080", null);
        // Different commitTo
        GitRequestDetails details = pushDetailsFor("bbbbbb", "refs/heads/main", "repo");
        HttpServletRequest req = mockPushRequest(details);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filter.doHttpFilter(req, resp);

        verify(req, never()).setAttribute(eq(PRE_APPROVED_ATTR), any());
    }

    @Test
    void tagPush_approvedRecord_setsPreApproved() throws Exception {
        // Tag pushes have commit == null; the filter must still honour a prior approval.
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        PushRecord approved = PushRecord.builder()
                .commitTo("tagsha123")
                .branch("refs/tags/v1.0")
                .provider("github")
                .project("owner")
                .repoName("my-repo")
                .build();
        store.save(approved);
        store.approve(
                approved.getId(),
                Attestation.builder()
                        .pushId(approved.getId())
                        .type(Attestation.Type.APPROVAL)
                        .reviewerUsername("admin")
                        .build());

        AllowApprovedPushFilter filter = new AllowApprovedPushFilter(store, "http://localhost:8080", null);

        // No commit set — mirrors what ParseGitRequestFilter produces for a tag push
        GitRequestDetails details = new GitRequestDetails();
        details.setCommitTo("tagsha123");
        details.setBranch("refs/tags/v1.0");
        details.setProvider(GitHubProvider.builder().name("github").build());
        details.setRepoRef(GitRequestDetails.RepoRef.builder()
                .owner("owner")
                .name("my-repo")
                .slug("/owner/my-repo")
                .build());

        HttpServletRequest req = mockPushRequest(details);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filter.doHttpFilter(req, resp);

        verify(req).setAttribute(PRE_APPROVED_ATTR, Boolean.TRUE);
    }

    /**
     * The approval belongs to one repository on one provider. Repo names recur across an estate, so matching on the
     * bare name alone would let an approval for one org's repo carry a push to another org's same-named repo.
     */
    @Test
    void approvedRecordForSameRepoNameInAnotherOrg_doesNotSetPreApproved() throws Exception {
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        PushRecord approved = PushRecord.builder()
                .commitTo("deadbeef")
                .branch("refs/heads/main")
                .provider("github")
                .project("acme")
                .repoName("app")
                .build();
        store.save(approved);
        store.approve(
                approved.getId(),
                Attestation.builder()
                        .pushId(approved.getId())
                        .type(Attestation.Type.APPROVAL)
                        .reviewerUsername("admin")
                        .build());

        AllowApprovedPushFilter filter = new AllowApprovedPushFilter(store, "http://localhost:8080", null);
        GitRequestDetails details = pushDetailsFor("deadbeef", "refs/heads/main", "app", "evil-org", "github");
        HttpServletRequest req = mockPushRequest(details);

        filter.doHttpFilter(req, mock(HttpServletResponse.class));

        verify(req, never()).setAttribute(eq(PRE_APPROVED_ATTR), any());
    }

    /** Same owner and repo name, different configured provider — still a different repository. */
    @Test
    void approvedRecordOnAnotherProvider_doesNotSetPreApproved() throws Exception {
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        PushRecord approved = PushRecord.builder()
                .commitTo("deadbeef")
                .branch("refs/heads/main")
                .provider("github")
                .project("acme")
                .repoName("app")
                .build();
        store.save(approved);
        store.approve(
                approved.getId(),
                Attestation.builder()
                        .pushId(approved.getId())
                        .type(Attestation.Type.APPROVAL)
                        .reviewerUsername("admin")
                        .build());

        AllowApprovedPushFilter filter = new AllowApprovedPushFilter(store, "http://localhost:8080", null);
        GitRequestDetails details = pushDetailsFor("deadbeef", "refs/heads/main", "app", "acme", "internal-gitea");
        HttpServletRequest req = mockPushRequest(details);

        filter.doHttpFilter(req, mock(HttpServletResponse.class));

        verify(req, never()).setAttribute(eq(PRE_APPROVED_ATTR), any());
    }

    /** Without a full repository identity the lookup cannot be scoped, so it must not run at all. */
    @Test
    void missingProvider_skipsLookup() throws Exception {
        PushStore store = mock(PushStore.class);
        AllowApprovedPushFilter filter = new AllowApprovedPushFilter(store, "http://localhost:8080", null);
        GitRequestDetails details = pushDetailsFor("deadbeef", "refs/heads/main", "app");
        details.setProvider(null);
        HttpServletRequest req = mockPushRequest(details);

        filter.doHttpFilter(req, mock(HttpServletResponse.class));

        verify(store, never()).find(any());
    }

    @Test
    void blankCommitTo_skipsLookup() throws Exception {
        PushStore store = mock(PushStore.class);
        AllowApprovedPushFilter filter = new AllowApprovedPushFilter(store, "http://localhost:8080", null);
        GitRequestDetails details = pushDetailsFor("", "refs/heads/main", "repo");
        HttpServletRequest req = mockPushRequest(details);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filter.doHttpFilter(req, resp);

        verify(store, never()).find(any());
    }

    // ── Supersede ──────────────────────────────────────────────────────────────

    @Test
    void newPushToSameBranch_cancelsOlderPendingRecord() throws Exception {
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        PushRecord pending = PushRecord.builder()
                .commitTo("oldsha")
                .branch("refs/heads/main")
                .provider("github")
                .project("owner")
                .repoName("my-repo")
                .status(PushStatus.PENDING)
                .build();
        store.save(pending);

        AllowApprovedPushFilter filter = new AllowApprovedPushFilter(store, "http://localhost:8080", null);
        GitRequestDetails details = pushDetailsFor("newsha", "refs/heads/main", "my-repo");
        HttpServletRequest req = mockPushRequest(details);

        filter.doHttpFilter(req, mock(HttpServletResponse.class));

        PushRecord reloaded = store.findById(pending.getId()).orElseThrow();
        assertEquals(PushStatus.CANCELED, reloaded.getStatus());
        assertTrue(
                reloaded.getAttestation().getReason().contains(details.getId().toString()));
    }

    @Test
    void newPushToDifferentBranch_doesNotCancelPendingRecord() throws Exception {
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        PushRecord pending = PushRecord.builder()
                .commitTo("oldsha")
                .branch("refs/heads/other")
                .provider("github")
                .project("owner")
                .repoName("my-repo")
                .status(PushStatus.PENDING)
                .build();
        store.save(pending);

        AllowApprovedPushFilter filter = new AllowApprovedPushFilter(store, "http://localhost:8080", null);
        GitRequestDetails details = pushDetailsFor("newsha", "refs/heads/main", "my-repo");
        HttpServletRequest req = mockPushRequest(details);

        filter.doHttpFilter(req, mock(HttpServletResponse.class));

        PushRecord reloaded = store.findById(pending.getId()).orElseThrow();
        assertEquals(PushStatus.PENDING, reloaded.getStatus());
    }

    @Test
    void newPushToSameBranch_doesNotCancelApprovedRecord() throws Exception {
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        PushRecord approved = PushRecord.builder()
                .commitTo("oldsha")
                .branch("refs/heads/main")
                .provider("github")
                .project("owner")
                .repoName("my-repo")
                .build();
        store.save(approved);
        store.approve(
                approved.getId(),
                Attestation.builder()
                        .pushId(approved.getId())
                        .type(Attestation.Type.APPROVAL)
                        .reviewerUsername("admin")
                        .build());

        AllowApprovedPushFilter filter = new AllowApprovedPushFilter(store, "http://localhost:8080", null);
        GitRequestDetails details = pushDetailsFor("newsha", "refs/heads/main", "my-repo");
        HttpServletRequest req = mockPushRequest(details);

        filter.doHttpFilter(req, mock(HttpServletResponse.class));

        PushRecord reloaded = store.findById(approved.getId()).orElseThrow();
        assertEquals(PushStatus.APPROVED, reloaded.getStatus());
    }

    @Test
    void noPendingRecordForBranch_findIsStillScoped() throws Exception {
        PushStore store = mock(PushStore.class);
        when(store.find(any())).thenReturn(java.util.List.of());
        AllowApprovedPushFilter filter = new AllowApprovedPushFilter(store, "http://localhost:8080", null);
        GitRequestDetails details = pushDetailsFor("newsha", "refs/heads/main", "my-repo");
        HttpServletRequest req = mockPushRequest(details);

        filter.doHttpFilter(req, mock(HttpServletResponse.class));

        verify(store)
                .find(argThat((PushQuery q) ->
                        q.getStatus() == PushStatus.PENDING && "refs/heads/main".equals(q.getBranch())));
        verify(store, never()).cancel(any(), any());
    }
}
