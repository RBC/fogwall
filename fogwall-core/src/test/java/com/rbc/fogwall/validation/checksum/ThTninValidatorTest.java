package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ThTninValidatorTest {

    private final ThTninValidator validator = new ThTninValidator();

    @Test
    void validTnin_passes() {
        assertTrue(validator.isValid("1100000000008"));
    }

    @Test
    void invalidChecksum_fails() {
        assertFalse(validator.isValid("1100000000009"));
    }

    @Test
    void wrongLength_fails() {
        assertFalse(validator.isValid("110000000000"));
    }

    @Test
    void nonDigit_fails() {
        assertFalse(validator.isValid("110000000000A"));
    }
}
