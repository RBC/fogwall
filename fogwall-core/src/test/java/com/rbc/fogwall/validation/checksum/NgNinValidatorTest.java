package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NgNinValidatorTest {

    private final NgNinValidator validator = new NgNinValidator();

    @Test
    void validNin_passes() {
        assertTrue(validator.isValid("23456789019"));
    }

    @Test
    void invalidChecksum_fails() {
        assertFalse(validator.isValid("23456789018"));
    }

    @Test
    void wrongLength_fails() {
        assertFalse(validator.isValid("2345678901"));
    }

    @Test
    void nonDigit_fails() {
        assertFalse(validator.isValid("2345678901A"));
    }
}
