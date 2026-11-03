package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.config.SecretScanConfig;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.git.Commit;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.GitleaksRunner;
import com.rbc.fogwall.git.HttpOperation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecretScanningFilterTest {

    @TempDir
    File tempDir;

    SecretScanConfig enabledConfig;
    GitleaksRunner runner;
    SecretScanningFilter filter;

    @BeforeEach
    void setUp() {
        enabledConfig = SecretScanConfig.builder().enabled(true).build();
        runner = mock(GitleaksRunner.class);
        filter = new SecretScanningFilter(enabledConfig, runner);
    }

    private HttpServletRequest requestWith(GitRequestDetails details) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute(GIT_REQUEST_ATTR)).thenReturn(details);
        return req;
    }

    private GitRequestDetails pushDetailsWithRepo() {
        Repository repo = mock(Repository.class);
        when(repo.getDirectory()).thenReturn(tempDir);
        GitRequestDetails details = new GitRequestDetails();
        details.setOperation(HttpOperation.PUSH);
        details.setCommitTo("abc123");
        details.setCommitFrom("def456");
        details.setLocalRepository(repo);
        return details;
    }

    // ---- early returns ----

    @Test
    void nullDetails_skipped() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute(GIT_REQUEST_ATTR)).thenReturn(null);

        filter.doHttpFilter(req, mock(HttpServletResponse.class));

        verifyNoInteractions(runner);
    }

    @Test
    void disabledConfig_skipped() throws Exception {
        SecretScanningFilter disabledFilter = new SecretScanningFilter(
                SecretScanConfig.builder().enabled(false).build(), runner);
        GitRequestDetails details = pushDetailsWithRepo();

        disabledFilter.doFilter(requestWith(details), mock(HttpServletResponse.class), mock(FilterChain.class));
        assertTrue(details.getSteps().isEmpty());

        verifyNoInteractions(runner);
    }

    @Test
    void nullLocalRepository_skipped() throws Exception {
        GitRequestDetails details = new GitRequestDetails();
        details.setOperation(HttpOperation.PUSH);
        details.setCommitTo("abc123");
        // localRepository not set

        filter.doHttpFilter(requestWith(details), mock(HttpServletResponse.class));

        verifyNoInteractions(runner);
    }

    @Test
    void nullCommitTo_skipped() throws Exception {
        Repository repo = mock(Repository.class);
        when(repo.getDirectory()).thenReturn(tempDir);
        GitRequestDetails details = new GitRequestDetails();
        details.setOperation(HttpOperation.PUSH);
        details.setLocalRepository(repo);
        // commitTo is null

        filter.doHttpFilter(requestWith(details), mock(HttpServletResponse.class));

        verifyNoInteractions(runner);
    }

    @Test
    void blankCommitTo_skipped() throws Exception {
        Repository repo = mock(Repository.class);
        when(repo.getDirectory()).thenReturn(tempDir);
        GitRequestDetails details = new GitRequestDetails();
        details.setOperation(HttpOperation.PUSH);
        details.setLocalRepository(repo);
        details.setCommitTo("   ");

        filter.doHttpFilter(requestWith(details), mock(HttpServletResponse.class));

        verifyNoInteractions(runner);
    }

    // ---- scanner results ----

    @Test
    void scannerUnavailable_failClosed() throws Exception {
        GitRequestDetails details = pushDetailsWithRepo();
        when(runner.scanGit(any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        filter.doHttpFilter(requestWith(details), mock(HttpServletResponse.class));

        // fail-closed: scanner error with enabled=true must block the push
        assertFalse(details.getSteps().isEmpty());
        assertTrue(
                details.getSteps().stream().anyMatch(s -> s.getStatus() == StepStatus.FAIL),
                "Scanner error must produce a FAIL step when secret-scan is enabled");
    }

    @Test
    void noFindings_passes() throws Exception {
        GitRequestDetails details = pushDetailsWithRepo();
        when(runner.scanGit(any(), any(), any(), any(), any())).thenReturn(Optional.of(List.of()));

        filter.doHttpFilter(requestWith(details), mock(HttpServletResponse.class));

        assertTrue(details.getSteps().isEmpty());
    }

    @Test
    void withFindings_recordsIssue() throws Exception {
        GitRequestDetails details = pushDetailsWithRepo();
        GitleaksRunner.Finding finding = mock(GitleaksRunner.Finding.class);
        when(finding.toMessage()).thenReturn("leaked-secret in config.yml:42");
        when(runner.scanGit(any(), any(), any(), any(), any())).thenReturn(Optional.of(List.of(finding)));
        HttpServletRequest req = requestWith(details);

        filter.doHttpFilter(req, mock(HttpServletResponse.class));

        assertFalse(details.getSteps().isEmpty(), "Finding must be recorded as a step");
        assertNotNull(details.getSteps().get(0).getErrorMessage());
    }

    // ---- include-metadata: commit and tag messages ----

    private SecretScanningFilter metadataFilter() {
        return new SecretScanningFilter(
                SecretScanConfig.builder().enabled(true).includeMetadata(true).build(), runner);
    }

    @Test
    void includeMetadataOff_messagesNotScanned() throws Exception {
        GitRequestDetails details = pushDetailsWithRepo();
        details.getPushedCommits()
                .add(Commit.builder().sha("1234567890abcdef").message("msg").build());
        when(runner.scanGit(any(), any(), any(), any(), any())).thenReturn(Optional.of(List.of()));

        filter.doHttpFilter(requestWith(details), mock(HttpServletResponse.class));

        verify(runner, never()).scanMessages(any(), any());
    }

    @Test
    void includeMetadata_scansPushedCommitAndTagMessages() throws Exception {
        GitRequestDetails details = pushDetailsWithRepo();
        details.setBranch("refs/tags/v2.0.0");
        details.getPushedCommits()
                .add(Commit.builder()
                        .sha("1234567890abcdef")
                        .message("fix build")
                        .build());
        details.setTagMessage("release v2");
        when(runner.scanGit(any(), any(), any(), any(), any())).thenReturn(Optional.of(List.of()));
        when(runner.scanMessages(any(), any())).thenReturn(Optional.of(List.of()));

        metadataFilter().doHttpFilter(requestWith(details), mock(HttpServletResponse.class));

        verify(runner)
                .scanMessages(
                        eq(List.of(
                                new GitleaksRunner.ScannedMessage("message of commit 1234567", "fix build"),
                                new GitleaksRunner.ScannedMessage("message of tag v2.0.0", "release v2"))),
                        any());
        assertTrue(details.getSteps().isEmpty());
    }

    @Test
    void includeMetadata_messageFinding_recordsIssueAndQueuesRedaction() throws Exception {
        GitRequestDetails details = pushDetailsWithRepo();
        details.getPushedCommits()
                .add(Commit.builder().sha("1234567890abcdef").message("token").build());
        GitleaksRunner.Finding finding = new GitleaksRunner.Finding();
        finding.setRuleId("github-pat");
        finding.setSecret("ghp_secretvalue");
        finding.setMatch("ghp_secretvalue");
        finding.setMessageLocation("message of commit 1234567");
        when(runner.scanGit(any(), any(), any(), any(), any())).thenReturn(Optional.of(List.of()));
        when(runner.scanMessages(any(), any())).thenReturn(Optional.of(List.of(finding)));

        metadataFilter().doHttpFilter(requestWith(details), mock(HttpServletResponse.class));

        assertTrue(details.getSteps().stream().anyMatch(s -> s.getStatus() == StepStatus.FAIL));
        assertTrue(details.getSteps().get(0).getErrorMessage().contains("message of commit 1234567"));
        assertFalse(
                details.getSteps().get(0).getErrorMessage().contains("ghp_secretvalue"),
                "the raw secret must never reach the client message");
        assertEquals(List.of("ghp_secretvalue"), details.getSecretsToRedact());
    }

    @Test
    void includeMetadata_messageScannerUnavailable_failClosed() throws Exception {
        GitRequestDetails details = pushDetailsWithRepo();
        details.getPushedCommits()
                .add(Commit.builder().sha("1234567890abcdef").message("msg").build());
        when(runner.scanGit(any(), any(), any(), any(), any())).thenReturn(Optional.of(List.of()));
        when(runner.scanMessages(any(), any())).thenReturn(Optional.empty());

        metadataFilter().doHttpFilter(requestWith(details), mock(HttpServletResponse.class));

        assertTrue(
                details.getSteps().stream().anyMatch(s -> s.getStatus() == StepStatus.FAIL),
                "a message scan that could not run must block when secret-scan is enabled");
    }
}
