package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.PushCommit;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStep;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SecretRedactorTest {

    private static PushRecord recordWithDiff(String diffContent) {
        PushRecord record = PushRecord.builder().build();
        List<PushStep> steps = new ArrayList<>();
        steps.add(PushStep.builder().stepName("diff").content(diffContent).build());
        record.setSteps(steps);
        return record;
    }

    // ---- multi-line secret, whole diff lines ----

    @Test
    void multiLineSecret_wholeDiffLines_redacted() {
        String diff = """
                diff --git a/private.key b/private.key
                +-----BEGIN PRIVATE KEY-----
                +MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcw
                +-----END PRIVATE KEY-----
                """;
        String secret =
                "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcw\n-----END PRIVATE KEY-----\n";

        PushRecord record = recordWithDiff(diff);
        SecretRedactor.redact(record, List.of(secret));

        String content = record.getSteps().get(0).getContent();
        assertFalse(content.contains("MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcw"), "key material must be gone");
        assertTrue(content.contains("+[REDACTED]"), "prefix must be preserved, content replaced");
        // Each redacted line keeps exactly one prefix char - not doubled, not dropped.
        assertTrue(content.lines().anyMatch(l -> l.equals("+[REDACTED]")));
    }

    // ---- secret embedded mid-line ----

    @Test
    void secretEmbeddedMidLine_redacted_surroundingTextPreserved() {
        String diff = """
                diff --git a/config.txt b/config.txt
                +stripe_key = "sk_test_abc123"
                """;

        PushRecord record = recordWithDiff(diff);
        SecretRedactor.redact(record, List.of("sk_test_abc123"));

        String content = record.getSteps().get(0).getContent();
        assertFalse(content.contains("sk_test_abc123"));
        assertTrue(content.contains("+stripe_key = \"[REDACTED]\""), "surrounding line text must survive");
    }

    // ---- non-matching diff lines are untouched, even with a +/-/space prefix ----

    @Test
    void nonMatchingDiffLines_untouched() {
        String diff = """
                diff --git a/f.txt b/f.txt
                -old unrelated line
                +new unrelated line
                 context line
                """;

        PushRecord record = recordWithDiff(diff);
        SecretRedactor.redact(record, List.of("sk_test_abc123"));

        assertEquals(diff, record.getSteps().get(0).getContent(), "no match anywhere - diff must be byte-identical");
    }

    // ---- non-diff text (error/blocked messages) still gets substring redaction ----

    @Test
    void nonDiffText_errorMessage_stillRedacted() {
        PushRecord record = PushRecord.builder().build();
        List<PushStep> steps = new ArrayList<>();
        steps.add(PushStep.builder()
                .stepName("scanSecrets")
                .errorMessage("leaked value: sk_test_abc123 in config.txt")
                .build());
        record.setSteps(steps);

        SecretRedactor.redact(record, List.of("sk_test_abc123"));

        String msg = record.getSteps().get(0).getErrorMessage();
        assertFalse(msg.contains("sk_test_abc123"));
        assertTrue(msg.contains("[REDACTED]"));
    }

    @Test
    void recordBlockedMessage_alsoRedacted() {
        PushRecord record = PushRecord.builder().build();
        record.setSteps(new ArrayList<>());
        record.setBlockedMessage("Found secret sk_test_abc123 in diff");

        SecretRedactor.redact(record, List.of("sk_test_abc123"));

        assertFalse(record.getBlockedMessage().contains("sk_test_abc123"));
        assertTrue(record.getBlockedMessage().contains("[REDACTED]"));
    }

    // ---- commit message redaction (content-pattern findings, not just secrets) ----

    @Test
    void commitMessage_containingMatch_redacted() {
        PushRecord record = PushRecord.builder().build();
        record.setSteps(new ArrayList<>());
        PushCommit commit = PushCommit.builder()
                .message("Fixed bug reported by customer, SIN 123456782 was affected")
                .build();
        record.setCommits(new ArrayList<>(List.of(commit)));

        SecretRedactor.redact(record, List.of("123456782"));

        String message = record.getCommits().get(0).getMessage();
        assertFalse(message.contains("123456782"));
        assertTrue(message.contains("[REDACTED]"));
        assertTrue(message.startsWith("Fixed bug reported by customer, SIN"), "surrounding text must survive");
    }

    @Test
    void nullCommits_doesNotThrow() {
        PushRecord record = PushRecord.builder().build();
        record.setSteps(new ArrayList<>());
        record.setCommits(null);

        assertDoesNotThrow(() -> SecretRedactor.redact(record, List.of("123456782")));
    }

    // ---- no-ops ----

    @Test
    void emptySecrets_noOp() {
        String diff = "+stripe_key = \"sk_test_abc123\"\n";
        PushRecord record = recordWithDiff(diff);

        SecretRedactor.redact(record, List.of());

        assertEquals(diff, record.getSteps().get(0).getContent());
    }

    @Test
    void nullSecrets_noOp() {
        String diff = "+stripe_key = \"sk_test_abc123\"\n";
        PushRecord record = recordWithDiff(diff);

        SecretRedactor.redact(record, null);

        assertEquals(diff, record.getSteps().get(0).getContent());
    }

    @Test
    void nullSteps_doesNotThrow() {
        PushRecord record = PushRecord.builder().build();
        record.setSteps(null);

        assertDoesNotThrow(() -> SecretRedactor.redact(record, List.of("sk_test_abc123")));
    }

    @Test
    void blankAndNullSecretEntries_ignored() {
        String diff = "+stripe_key = \"sk_test_abc123\"\n";
        PushRecord record = recordWithDiff(diff);

        SecretRedactor.redact(record, java.util.Arrays.asList(null, "", "   ", "sk_test_abc123"));

        assertFalse(record.getSteps().get(0).getContent().contains("sk_test_abc123"));
    }
}
