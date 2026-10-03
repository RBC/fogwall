package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiffGenerationFilterTest {

    @TempDir
    Path repoDir;

    Repository repo;
    String baseCommit;
    String tipCommit;

    @BeforeEach
    void setUp() throws Exception {
        Git git = Git.init().setDirectory(repoDir.toFile()).call();
        repo = git.getRepository();
        repo.getConfig().setBoolean("commit", null, "gpgsign", false);
        repo.getConfig().save();
        baseCommit = commit(git, "init.txt", "initial content").name();
        tipCommit = commit(git, "added.txt", "an added line").name();
    }

    private RevCommit commit(Git git, String filename, String content) throws Exception {
        Files.writeString(repoDir.resolve(filename), content + "\n");
        git.add().addFilepattern(".").call();
        return git.commit()
                .setAuthor(new PersonIdent("Dev", "dev@example.com"))
                .setCommitter(new PersonIdent("Dev", "dev@example.com"))
                .setMessage("add " + filename)
                .call();
    }

    private GitRequestDetails pushDetails() {
        GitRequestDetails details = new GitRequestDetails();
        details.setOperation(HttpOperation.PUSH);
        details.setBranch("refs/heads/main");
        details.setCommitFrom(baseCommit);
        details.setCommitTo(tipCommit);
        details.setLocalRepository(repo);
        return details;
    }

    private static HttpServletRequest request(GitRequestDetails details) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getContentType()).thenReturn("application/x-git-receive-pack-request");
        when(req.getRequestURI()).thenReturn("/proxy/github.com/owner/repo.git/git-receive-pack");
        when(req.getAttribute(GIT_REQUEST_ATTR)).thenReturn(details);
        return req;
    }

    private static void run(GitRequestDetails details) throws Exception {
        new DiffGenerationFilter().doFilter(request(details), mock(HttpServletResponse.class), mock(FilterChain.class));
    }

    @Test
    void branchPush_recordsDiffStepWithContent() throws Exception {
        GitRequestDetails details = pushDetails();

        run(details);

        PushStep diffStep = details.getSteps().stream()
                .filter(s -> "diff".equals(s.getStepName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("diff step not recorded"));
        assertEquals(StepStatus.PASS, diffStep.getStatus());
        assertTrue(diffStep.getContent().contains("an added line"));
        assertEquals(1, details.getSteps().size(), "only the diff step is recorded");
    }

    @Test
    void tagPush_recordsNoStep() throws Exception {
        GitRequestDetails details = pushDetails();
        details.setBranch("refs/tags/v1.0.0");

        run(details);

        assertTrue(details.getSteps().isEmpty(), "a tag push has no diff and must record no step");
    }

    @Test
    void missingLocalRepository_recordsSkipped() throws Exception {
        GitRequestDetails details = pushDetails();
        details.setLocalRepository(null);

        run(details);

        assertEquals(1, details.getSteps().size());
        assertEquals("diff-generation", details.getSteps().get(0).getStepName());
        assertEquals(StepStatus.SKIPPED, details.getSteps().get(0).getStatus());
    }
}
