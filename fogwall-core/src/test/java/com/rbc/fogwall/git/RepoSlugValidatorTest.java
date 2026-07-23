package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RepoSlugValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"repo", "my-repo", "my_repo", "my.repo", "Repo123", "a", "0", "repo.git", "...dots"})
    void validSegments(String segment) {
        assertTrue(RepoSlugValidator.isValidSegment(segment));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "", " ", ".", "..", "a/b", "a\\b", "a b", "a\tb", "a\rb", "a\nb", "%2e%2e", "a?b", "a#b", "a:b", "a@b",
                "über"
            })
    void invalidSegments(String segment) {
        assertFalse(RepoSlugValidator.isValidSegment(segment));
    }

    @Test
    void nullSegment_isInvalid() {
        assertFalse(RepoSlugValidator.isValidSegment(null));
    }
}
