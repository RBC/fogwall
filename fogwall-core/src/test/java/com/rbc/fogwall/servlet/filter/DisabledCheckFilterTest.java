package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.config.BinaryBlobConfig;
import com.rbc.fogwall.config.ContentPatternConfig;
import com.rbc.fogwall.config.GpgConfig;
import com.rbc.fogwall.config.SecretScanConfig;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.GitleaksRunner;
import com.rbc.fogwall.git.HttpOperation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * A transparent-proxy check turned off in config is passed over by the filter chain: it does not run and records no
 * step, so the push summary and the push record do not mention it. Enablement is read per request, so a config reload
 * shows or hides the step on the next push.
 */
class DisabledCheckFilterTest {

    private static GitRequestDetails pushDetails() {
        GitRequestDetails details = new GitRequestDetails();
        details.setOperation(HttpOperation.PUSH);
        details.setBranch("refs/heads/main");
        details.setCommitFrom("1111111111111111111111111111111111111111");
        details.setCommitTo("2222222222222222222222222222222222222222");
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

    /** Runs the filter through the chain template and returns the push's recorded steps. */
    private static GitRequestDetails run(FogwallFilter filter) throws Exception {
        GitRequestDetails details = pushDetails();
        HttpServletRequest req = request(details);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, resp, chain);
        verify(chain).doFilter(req, resp);
        return details;
    }

    private static void assertNoStep(FogwallFilter filter) throws Exception {
        assertFalse(filter.enabled());
        GitRequestDetails details = run(filter);
        assertTrue(details.getSteps().isEmpty(), filter.getStepName() + " is off and must record no step");
        assertTrue(details.getFilters().isEmpty(), filter.getStepName() + " is off and must not be listed as run");
        assertEquals(GitRequestDetails.GitResult.PENDING, details.getResult());
    }

    @Test
    void binaryBlobDisabled_recordsNoStep() throws Exception {
        assertNoStep(
                new BinaryBlobFilter(BinaryBlobConfig.builder().enabled(false).build()));
    }

    @Test
    void secretScanDisabled_recordsNoStep_andNeverInvokesScanner() throws Exception {
        GitleaksRunner runner = mock(GitleaksRunner.class);
        assertNoStep(new SecretScanningFilter(
                SecretScanConfig.builder().enabled(false).build(), runner));
        verifyNoInteractions(runner);
    }

    @Test
    void gpgDisabled_recordsNoStep() throws Exception {
        assertNoStep(new GpgSignatureFilter(GpgConfig.defaultConfig()));
    }

    @Test
    void contentPatternsDisabled_recordsNoStepForEitherSource() throws Exception {
        ContentPatternConfig off = ContentPatternConfig.defaultConfig();
        assertNoStep(new ContentPatternMessageFilter(off));
        assertNoStep(new ContentPatternDiffFilter(off));
    }

    @Test
    void contentPatternsEnabledWithNoBundles_recordsNoStep() throws Exception {
        ContentPatternConfig noBundles =
                ContentPatternConfig.builder().enabled(true).bundles(List.of()).build();
        assertNoStep(new ContentPatternMessageFilter(noBundles));
        assertNoStep(new ContentPatternDiffFilter(noBundles));
    }

    @Test
    void contentPatternSourceTurnedOff_recordsNoStepForThatSourceOnly() throws Exception {
        ContentPatternConfig messagesOff = ContentPatternConfig.builder()
                .enabled(true)
                .bundles(List.of("national-id-us"))
                .scanCommitMessages(false)
                .build();
        assertNoStep(new ContentPatternMessageFilter(messagesOff));
        assertTrue(new ContentPatternDiffFilter(messagesOff).enabled());

        ContentPatternConfig diffOff = ContentPatternConfig.builder()
                .enabled(true)
                .bundles(List.of("national-id-us"))
                .scanDiff(false)
                .build();
        assertNoStep(new ContentPatternDiffFilter(diffOff));
        assertTrue(new ContentPatternMessageFilter(diffOff).enabled());
    }

    @Test
    void enabledGpg_stillRecordsStep() throws Exception {
        GitRequestDetails details =
                run(new GpgSignatureFilter(GpgConfig.builder().enabled(true).build()));

        assertEquals(1, details.getSteps().size());
        assertEquals("gpg-signature", details.getSteps().get(0).getStepName());
        assertEquals(StepStatus.PASS, details.getSteps().get(0).getStatus());
    }

    @Test
    void reload_togglesStepVisibilityOnTheNextPush() throws Exception {
        AtomicReference<ContentPatternConfig> live = new AtomicReference<>(ContentPatternConfig.defaultConfig());
        var filter = new ContentPatternMessageFilter(live::get);

        assertTrue(run(filter).getSteps().isEmpty());

        live.set(ContentPatternConfig.builder()
                .enabled(true)
                .bundles(List.of("national-id-us"))
                .build());
        GitRequestDetails enabledPush = run(filter);
        assertEquals(1, enabledPush.getSteps().size());
        assertEquals("content-pattern-message", enabledPush.getSteps().get(0).getStepName());

        live.set(ContentPatternConfig.defaultConfig());
        assertTrue(run(filter).getSteps().isEmpty());
    }
}
