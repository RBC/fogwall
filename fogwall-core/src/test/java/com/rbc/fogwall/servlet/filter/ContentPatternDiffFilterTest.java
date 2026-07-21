package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.config.ContentPatternConfig;
import com.rbc.fogwall.db.model.StepStatus;
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

class ContentPatternDiffFilterTest {

    @TempDir
    Path repoDir;

    Repository repo;
    String baseCommit;
    String cleanCommit;
    String matchingCommit;

    @BeforeEach
    void setUp() throws Exception {
        Git git = Git.init().setDirectory(repoDir.toFile()).call();
        repo = git.getRepository();
        repo.getConfig().setBoolean("commit", null, "gpgsign", false);
        repo.getConfig().save();

        baseCommit = commit(git, "init.txt", "initial content").name();
        cleanCommit = commit(git, "clean.txt", "perfectly fine addition").name();
        matchingCommit =
                commit(git, "customer.txt", "# ssn on file: 212-96-7431").name();
    }

    private RevCommit commit(Git git, String filename, String content) throws Exception {
        File f = repoDir.resolve(filename).toFile();
        Files.writeString(f.toPath(), content + "\n");
        git.add().addFilepattern(".").call();
        return git.commit()
                .setAuthor(new PersonIdent("Dev", "dev@example.com"))
                .setCommitter(new PersonIdent("Dev", "dev@example.com"))
                .setMessage("add " + filename)
                .call();
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

    private ContentPatternConfig enabledConfig() {
        return ContentPatternConfig.builder()
                .enabled(true)
                .bundles(List.of("national-id-us"))
                .build();
    }

    @Test
    void disabled_skipsEntirely() throws Exception {
        GitRequestDetails details = pushDetails(baseCommit, matchingCommit);
        var filter = new ContentPatternDiffFilter(ContentPatternConfig.defaultConfig());
        FakeResponse resp = new FakeResponse();

        filter.doHttpFilter(mockRequest(details), resp.mock);

        assertTrue(details.getSteps().isEmpty());
    }

    @Test
    void scanDiffDisabled_skipsEntirely_evenWhenEnabled() throws Exception {
        GitRequestDetails details = pushDetails(baseCommit, matchingCommit);
        ContentPatternConfig config = ContentPatternConfig.builder()
                .enabled(true)
                .bundles(List.of("national-id-us"))
                .scanDiff(false)
                .build();
        var filter = new ContentPatternDiffFilter(config);
        FakeResponse resp = new FakeResponse();

        filter.doHttpFilter(mockRequest(details), resp.mock);

        assertTrue(details.getSteps().isEmpty());
    }

    @Test
    void matchInDiff_recordsWarnStep_doesNotBlock_andRedactsRawValue() throws Exception {
        GitRequestDetails details = pushDetails(baseCommit, matchingCommit);
        var filter = new ContentPatternDiffFilter(enabledConfig());
        FakeResponse resp = new FakeResponse();

        filter.doHttpFilter(mockRequest(details), resp.mock);

        assertEquals(GitRequestDetails.GitResult.PENDING, details.getResult(), "WARN must never reject a push");
        assertFalse(resp.committed.get());
        assertFalse(details.getSteps().isEmpty());
        var step = details.getSteps().get(0);
        assertEquals(StepStatus.WARN, step.getStatus());
        assertTrue(step.getContent().contains("Social Security Number"));
        assertFalse(step.getContent().contains("212-96-7431"), "raw matched value must never appear in step content");
        assertTrue(details.getSecretsToRedact().contains("212-96-7431"), "matched value must be queued for redaction");
    }

    @Test
    void cleanDiff_recordsPassStep() throws Exception {
        GitRequestDetails details = pushDetails(baseCommit, cleanCommit);
        var filter = new ContentPatternDiffFilter(enabledConfig());
        FakeResponse resp = new FakeResponse();

        filter.doHttpFilter(mockRequest(details), resp.mock);

        assertFalse(details.getSteps().isEmpty());
        assertEquals(StepStatus.PASS, details.getSteps().get(0).getStatus());
    }

    @Test
    void noLocalRepository_skipsGracefully() throws Exception {
        GitRequestDetails details = pushDetails(baseCommit, matchingCommit);
        details.setLocalRepository(null);
        var filter = new ContentPatternDiffFilter(enabledConfig());
        FakeResponse resp = new FakeResponse();

        filter.doHttpFilter(mockRequest(details), resp.mock);

        assertTrue(details.getSteps().isEmpty());
    }
}
