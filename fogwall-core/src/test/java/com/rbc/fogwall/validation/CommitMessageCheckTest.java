package com.rbc.fogwall.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.config.BlockConfig;
import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.config.MatchRule;
import com.rbc.fogwall.git.Commit;
import com.rbc.fogwall.git.Contributor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CommitMessageCheckTest {

    private static CommitConfig configWithBlockedLiterals(String... literals) {
        return CommitConfig.builder()
                .message(CommitConfig.MessageConfig.builder()
                        .block(BlockConfig.builder()
                                .rules(List.of(literals).stream()
                                        .map(MatchRule::literal)
                                        .toList())
                                .build())
                        .build())
                .build();
    }

    private static CommitConfig configWithBlockedPattern(String pattern) {
        return CommitConfig.builder()
                .message(CommitConfig.MessageConfig.builder()
                        .block(BlockConfig.builder()
                                .rules(List.of(MatchRule.regex(pattern)))
                                .build())
                        .build())
                .build();
    }

    private static Commit commitWithMessage(String message) {
        return Commit.builder()
                .sha("abc123")
                .author(Contributor.builder()
                        .name("Dev")
                        .email("dev@example.com")
                        .build())
                .committer(Contributor.builder()
                        .name("Dev")
                        .email("dev@example.com")
                        .build())
                .message(message)
                .build();
    }

    @Test
    void defaultConfig_cleanMessage_noViolations() {
        CommitMessageCheck check = new CommitMessageCheck(CommitConfig.defaultConfig());
        assertTrue(
                check.check(List.of(commitWithMessage("feat: add new feature"))).isEmpty());
    }

    @Test
    void blockedLiteral_caseMismatch_noViolation() {
        // Literal matching is case-sensitive: lowercase "wip" does not match the literal "WIP".
        CommitMessageCheck check = new CommitMessageCheck(configWithBlockedLiterals("WIP"));
        List<Violation> violations = check.check(List.of(commitWithMessage("wip: still in progress")));
        assertTrue(violations.isEmpty());
    }

    @Test
    void blockedLiteral_exactCase_returnsViolation() {
        CommitMessageCheck check = new CommitMessageCheck(configWithBlockedLiterals("fixup!"));
        assertFalse(check.check(List.of(commitWithMessage("fixup! previous commit")))
                .isEmpty());
    }

    @Test
    void blockedPattern_matches_returnsViolation() {
        CommitMessageCheck check = new CommitMessageCheck(configWithBlockedPattern("DO NOT MERGE"));
        assertFalse(check.check(List.of(commitWithMessage("DO NOT MERGE - work in progress")))
                .isEmpty());
    }

    @Test
    void emptyMessage_returnsViolation() {
        CommitMessageCheck check = new CommitMessageCheck(CommitConfig.defaultConfig());
        List<Violation> violations = check.check(List.of(commitWithMessage("")));
        assertFalse(violations.isEmpty());
        assertTrue(violations.get(0).reason().contains("empty commit message"));
    }

    @Test
    void cleanMessage_blockedLiteralsConfigured_noViolations() {
        CommitMessageCheck check = new CommitMessageCheck(configWithBlockedLiterals("WIP", "fixup!"));
        assertTrue(check.check(List.of(commitWithMessage("feat: implement login flow")))
                .isEmpty());
    }

    @Test
    void multipleCommits_eachCheckedIndependently() {
        CommitMessageCheck check = new CommitMessageCheck(configWithBlockedLiterals("fixup!"));
        List<Violation> violations = check.check(List.of(
                commitWithMessage("feat: clean commit"),
                commitWithMessage("fixup! previous"),
                commitWithMessage("fixup! another")));
        assertEquals(2, violations.size());
    }

    @Test
    void emptyCommitList_noViolations() {
        CommitMessageCheck check = new CommitMessageCheck(CommitConfig.defaultConfig());
        assertTrue(check.check(List.of()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"feat: new feature", "fix: resolve bug #42", "docs: update README", "chore: bump deps"})
    void conventionalCommits_noBlockedTerms_noViolations(String message) {
        CommitMessageCheck check = new CommitMessageCheck(configWithBlockedLiterals("WIP", "fixup!", "squash!"));
        assertTrue(check.check(List.of(commitWithMessage(message))).isEmpty());
    }

    @Test
    void violationContainsFirstLineOfMessage() {
        CommitMessageCheck check = new CommitMessageCheck(configWithBlockedLiterals("WIP"));
        String multiLine = "WIP: my change\n\nThis is the body of the commit message.";
        List<Violation> violations = check.check(List.of(commitWithMessage(multiLine)));
        assertFalse(violations.isEmpty());
        assertEquals("WIP: my change", violations.get(0).subject());
    }
}
