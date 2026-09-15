package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PushStepKindTest {

    @Test
    void displayOrderIsAContiguousFlatSequence() {
        Set<Integer> orders = Arrays.stream(PushStepKind.values())
                .map(PushStepKind::displayOrder)
                .collect(Collectors.toSet());
        assertEquals(PushStepKind.values().length, orders.size(), "every kind must have a distinct display order");
        Set<Integer> expected =
                IntStream.rangeClosed(1, PushStepKind.values().length).boxed().collect(Collectors.toSet());
        assertEquals(expected, orders, "display order must be a flat 1..N sequence, no gaps");
    }

    @Test
    void summarizableImpliesDisplayable() {
        for (PushStepKind kind : PushStepKind.values()) {
            if (kind.summarizable()) {
                assertTrue(kind.displayable(), kind + " is summarizable but not displayable");
            }
        }
    }

    @Test
    void summarizableKindsAreTheDeveloperFacingChecks() {
        Set<PushStepKind> summarizable = Arrays.stream(PushStepKind.values())
                .filter(PushStepKind::summarizable)
                .collect(Collectors.toSet());
        assertEquals(
                Set.of(
                        PushStepKind.URL_RULE,
                        PushStepKind.PUSH_PERMISSION,
                        PushStepKind.COMMIT_ATTRIBUTION,
                        PushStepKind.EMPTY_BRANCH,
                        PushStepKind.HIDDEN_COMMITS,
                        PushStepKind.AUTHOR_EMAIL,
                        PushStepKind.TRAILERS,
                        PushStepKind.COMMIT_MESSAGE,
                        PushStepKind.CONTENT_PATTERN_MESSAGE,
                        PushStepKind.BINARY_BLOB,
                        PushStepKind.DIFF_SCAN,
                        PushStepKind.GPG_SIGNATURE,
                        PushStepKind.SECRET_SCAN,
                        PushStepKind.CONTENT_PATTERN_DIFF),
                summarizable,
                "the set of steps shown in the git-client summary changed — confirm this is intended");
    }

    @Test
    void internalPlumbingIsNotDisplayable() {
        assertFalse(PushStepKind.PARSE_REQUEST.displayable());
        assertFalse(PushStepKind.DIFF_GENERATION.displayable());
        assertFalse(PushStepKind.BITBUCKET_CREDENTIAL_REWRITE.displayable());
        assertFalse(PushStepKind.PUSH_FINALIZER.displayable());
    }

    @Test
    void forKeyRoundTrips() {
        for (PushStepKind kind : PushStepKind.values()) {
            assertEquals(kind, PushStepKind.forKey(kind.key()).orElseThrow());
        }
        assertTrue(PushStepKind.forKey("not-a-real-kind").isEmpty());
    }
}
