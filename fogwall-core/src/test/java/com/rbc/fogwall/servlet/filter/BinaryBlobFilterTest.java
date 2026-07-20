package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.config.BinaryBlobConfig;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BinaryBlobFilterTest {

    @TempDir
    Path repoDir;

    Repository repo;
    Git git;
    String baseCommit;

    @BeforeEach
    void setUp() throws Exception {
        git = Git.init().setDirectory(repoDir.toFile()).call();
        repo = git.getRepository();
        repo.getConfig().setBoolean("commit", null, "gpgsign", false);
        repo.getConfig().save();

        baseCommit = commitText("init.txt", "initial content").name();
    }

    private RevCommit commitBytes(String filename, byte[] content) throws Exception {
        File f = repoDir.resolve(filename).toFile();
        Files.write(f.toPath(), content);
        git.add().addFilepattern(".").call();
        return git.commit()
                .setAuthor(new PersonIdent("Dev", "dev@example.com"))
                .setCommitter(new PersonIdent("Dev", "dev@example.com"))
                .setMessage("add " + filename)
                .call();
    }

    private RevCommit commitText(String filename, String content) throws Exception {
        return commitBytes(filename, (content + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static class FakeResponse {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        final AtomicBoolean committed = new AtomicBoolean(false);
        final HttpServletResponse mock;

        FakeResponse() throws IOException {
            mock = mock(HttpServletResponse.class);
            when(mock.getOutputStream()).thenReturn(new ServletOutputStream() {
                @Override
                public void write(int b) {
                    body.write(b);
                    committed.set(true);
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    body.write(b, off, len);
                    committed.set(true);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener l) {}
            });
            when(mock.isCommitted()).thenAnswer(inv -> committed.get());
        }
    }

    private static ServletInputStream emptyStream() {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        return new ServletInputStream() {
            @Override
            public int read() {
                return bais.read();
            }

            @Override
            public boolean isFinished() {
                return bais.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener l) {}
        };
    }

    private HttpServletRequest mockRequest(GitRequestDetails details) throws IOException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getContentType()).thenReturn("application/x-git-receive-pack-request");
        when(req.getRequestURI()).thenReturn("/proxy/github.com/owner/repo.git/git-receive-pack");
        when(req.getAttribute(GIT_REQUEST_ATTR)).thenReturn(details);
        when(req.getInputStream()).thenReturn(emptyStream());
        return req;
    }

    private GitRequestDetails pushDetails(String fromCommit, String toCommit) {
        GitRequestDetails details = new GitRequestDetails();
        details.setOperation(HttpOperation.PUSH);
        details.setRepoRef(GitRequestDetails.RepoRef.builder()
                .owner("owner")
                .name("repo")
                .slug("/owner/repo")
                .build());
        details.setCommitFrom(fromCommit);
        details.setCommitTo(toCommit);
        details.setLocalRepository(repo);
        return details;
    }

    private BinaryBlobFilter filterWithMaxSize(long maxSizeBytes) {
        BinaryBlobConfig config = BinaryBlobConfig.builder()
                .enabled(true)
                .maxSizeBytes(maxSizeBytes)
                .build();
        return new BinaryBlobFilter(config);
    }

    private BinaryBlobFilter disabledFilter() {
        return new BinaryBlobFilter(BinaryBlobConfig.defaultConfig());
    }

    // ---- disabled config → no-op ----

    @Test
    void disabledConfig_neverRuns() throws Exception {
        RevCommit tip = commitBytes("big.bin", new byte[2048]);
        GitRequestDetails details = pushDetails(baseCommit, tip.name());
        FakeResponse resp = new FakeResponse();

        disabledFilter().doHttpFilter(mockRequest(details), resp.mock);

        assertEquals(GitRequestDetails.GitResult.PENDING, details.getResult());
        assertTrue(details.getSteps().isEmpty());
    }

    // ---- clean push → PASS, no issue ----

    @Test
    void cleanPush_passes() throws Exception {
        RevCommit tip = commitText("clean.txt", "just text");
        GitRequestDetails details = pushDetails(baseCommit, tip.name());
        FakeResponse resp = new FakeResponse();

        filterWithMaxSize(1024).doHttpFilter(mockRequest(details), resp.mock);

        assertEquals(GitRequestDetails.GitResult.PENDING, details.getResult());
    }

    // ---- oversized blob → REJECTED ----

    @Test
    void oversizedBlob_recordsIssue() throws Exception {
        RevCommit tip = commitBytes("big.bin", new byte[2048]);
        GitRequestDetails details = pushDetails(baseCommit, tip.name());
        FakeResponse resp = new FakeResponse();

        filterWithMaxSize(1024).doHttpFilter(mockRequest(details), resp.mock);

        assertEquals(GitRequestDetails.GitResult.REJECTED, details.getResult());
    }

    // ---- null requestDetails → no-op ----

    @Test
    void nullDetails_noOp() throws Exception {
        HttpServletRequest req = mockRequest(null);
        when(req.getAttribute(GIT_REQUEST_ATTR)).thenReturn(null);
        FakeResponse resp = new FakeResponse();

        assertDoesNotThrow(() -> filterWithMaxSize(1024).doHttpFilter(req, resp.mock));
        assertFalse(resp.committed.get());
    }

    // ---- tag push → skipped ----

    @Test
    void tagPush_skipped() throws Exception {
        RevCommit tip = commitBytes("big.bin", new byte[2048]);
        GitRequestDetails details = pushDetails(baseCommit, tip.name());
        details.setBranch("refs/tags/v1.0.0");
        FakeResponse resp = new FakeResponse();

        filterWithMaxSize(1024).doHttpFilter(mockRequest(details), resp.mock);

        assertEquals(GitRequestDetails.GitResult.PENDING, details.getResult());
    }

    // ---- null localRepository → skip ----

    @Test
    void nullRepository_skips() throws Exception {
        RevCommit tip = commitBytes("big.bin", new byte[2048]);
        GitRequestDetails details = pushDetails(baseCommit, tip.name());
        details.setLocalRepository(null);
        FakeResponse resp = new FakeResponse();

        filterWithMaxSize(1024).doHttpFilter(mockRequest(details), resp.mock);

        assertEquals(GitRequestDetails.GitResult.PENDING, details.getResult());
    }

    // ---- per-commit scan catches a blob smuggled in an intermediate commit ----

    @Test
    void perCommitScan_oversizedBlobAddedInIntermediateCommitThenRemoved_stillCaught() throws Exception {
        commitBytes("big.bin", new byte[2048]);
        Files.delete(repoDir.resolve("big.bin"));
        git.rm().addFilepattern("big.bin").call();
        RevCommit tip = git.commit()
                .setAuthor(new PersonIdent("Dev", "dev@example.com"))
                .setCommitter(new PersonIdent("Dev", "dev@example.com"))
                .setMessage("remove big.bin")
                .call();

        GitRequestDetails details = pushDetails(baseCommit, tip.name());
        FakeResponse resp = new FakeResponse();

        filterWithMaxSize(1024).doHttpFilter(mockRequest(details), resp.mock);

        assertEquals(
                GitRequestDetails.GitResult.REJECTED,
                details.getResult(),
                "oversized blob smuggled in an intermediate commit must still be caught even though the aggregate diff is clean");
    }

    // ---- denied MIME type ----

    @Test
    void deniedMimeType_recordsIssue() throws Exception {
        RevCommit tip = commitBytes("doc.pdf", "%PDF-1.4\n...".getBytes(StandardCharsets.US_ASCII));
        GitRequestDetails details = pushDetails(baseCommit, tip.name());
        FakeResponse resp = new FakeResponse();

        BinaryBlobConfig config = BinaryBlobConfig.builder()
                .enabled(true)
                .denyMimeTypes(List.of("application/pdf"))
                .build();
        new BinaryBlobFilter(config).doHttpFilter(mockRequest(details), resp.mock);

        assertEquals(GitRequestDetails.GitResult.REJECTED, details.getResult());
    }
}
