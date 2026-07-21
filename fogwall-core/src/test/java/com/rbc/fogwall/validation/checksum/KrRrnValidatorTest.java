package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KrRrnValidatorTest {

    private final KrRrnValidator validator = new KrRrnValidator();

    @Test
    void validRrn_passes() {
        assertTrue(validator.isValid("9001010000003"));
    }

    @Test
    void invalidChecksum_fails() {
        assertFalse(validator.isValid("9001010000004"));
    }

    @Test
    void wrongLength_fails() {
        assertFalse(validator.isValid("900101000000"));
    }

    @Test
    void hyphenIsStripped() {
        assertTrue(validator.isValid("900101-0000003"));
    }
}
