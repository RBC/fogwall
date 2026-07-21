package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UkNhsNumberValidatorTest {

    private final UkNhsNumberValidator validator = new UkNhsNumberValidator();

    @Test
    void validNhsNumber_passes() {
        assertTrue(validator.isValid("9434765919"));
    }

    @Test
    void invalidChecksum_fails() {
        assertFalse(validator.isValid("9434765918"));
    }

    @Test
    void delimitersAreStripped() {
        assertTrue(validator.isValid("943 476 5919"));
    }

    @Test
    void wrongLength_fails() {
        assertFalse(validator.isValid("943476591"));
    }
}
