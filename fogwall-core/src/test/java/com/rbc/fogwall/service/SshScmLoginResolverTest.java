package com.rbc.fogwall.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.user.UserEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SshScmLoginResolverTest {

    private static final UserEntry ALICE = UserEntry.builder().username("alice").build();
    private static final GitHubProvider GITHUB = new GitHubProvider("/push");

    private static SshScmLoginResolver answering(String login, List<String> calls, String name) {
        return (user, provider, fingerprint) -> {
            calls.add(name);
            return Optional.ofNullable(login);
        };
    }

    @Test
    void firstAnswerWins_andLaterResolversAreNotConsulted() {
        List<String> calls = new ArrayList<>();
        SshScmLoginResolver chain = SshScmLoginResolver.firstOf(
                answering("alice-gh", calls, "first"), answering("alice-other", calls, "second"));

        assertEquals(Optional.of("alice-gh"), chain.resolveScmLogin(ALICE, GITHUB, "SHA256:fp"));
        assertEquals(List.of("first"), calls);
    }

    @Test
    void fallsThroughWhenTheFirstResolvesNothing() {
        List<String> calls = new ArrayList<>();
        SshScmLoginResolver chain =
                SshScmLoginResolver.firstOf(answering(null, calls, "first"), answering("alice-gh", calls, "second"));

        assertEquals(Optional.of("alice-gh"), chain.resolveScmLogin(ALICE, GITHUB, "SHA256:fp"));
        assertEquals(List.of("first", "second"), calls);
    }

    @Test
    void nullResolversAreSkipped() {
        List<String> calls = new ArrayList<>();
        SshScmLoginResolver chain = SshScmLoginResolver.firstOf(null, answering("alice-gh", calls, "only"), null);

        assertEquals(Optional.of("alice-gh"), chain.resolveScmLogin(ALICE, GITHUB, "SHA256:fp"));
    }

    @Test
    void noResolverAnswers_isEmpty() {
        List<String> calls = new ArrayList<>();
        SshScmLoginResolver chain =
                SshScmLoginResolver.firstOf(answering(null, calls, "first"), answering(null, calls, "second"));

        assertTrue(chain.resolveScmLogin(ALICE, GITHUB, "SHA256:fp").isEmpty());
        assertEquals(List.of("first", "second"), calls);
    }
}
