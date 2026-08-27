package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpstreamAuthProbeTest {

    /** Records what it was asked and answers from a script, so caching can be tested without a network. */
    private static class RecordingProbe extends UpstreamAuthProbe {
        final List<String> asked = new ArrayList<>();
        boolean answer;

        RecordingProbe(Duration ttl, boolean answer) {
            super(ttl);
            this.answer = answer;
        }

        @Override
        protected boolean probe(String upstreamRepoUrl) {
            asked.add(upstreamRepoUrl);
            return answer;
        }
    }

    @Test
    void aRepositoryIsProbedOncePerWindowHoweverManyClonesArrive() {
        var probe = new RecordingProbe(Duration.ofMinutes(5), false);

        for (int i = 0; i < 20; i++) {
            assertFalse(probe.requiresAuthentication("https://upstream.example/owner/repo.git"));
        }

        assertEquals(1, probe.asked.size(), "a burst of anonymous clones must cost one upstream round trip");
    }

    @Test
    void eachRepositoryGetsItsOwnVerdict() {
        var probe = new RecordingProbe(Duration.ofMinutes(5), true);

        probe.requiresAuthentication("https://upstream.example/owner/one.git");
        probe.requiresAuthentication("https://upstream.example/owner/two.git");

        assertEquals(2, probe.asked.size());
    }

    /**
     * A repository made private must stop being treated as public. Staleness cannot leak content on its own — the
     * anonymous upstream fetch that follows would fail — but it does turn a challenge into a confusing 404.
     */
    @Test
    void aVerdictIsRecheckedOnceItExpires() throws Exception {
        var probe = new RecordingProbe(Duration.ofMillis(1), false);

        assertFalse(probe.requiresAuthentication("https://upstream.example/owner/repo.git"));
        Thread.sleep(10);
        probe.answer = true;
        assertTrue(probe.requiresAuthentication("https://upstream.example/owner/repo.git"));

        assertEquals(2, probe.asked.size());
    }
}
