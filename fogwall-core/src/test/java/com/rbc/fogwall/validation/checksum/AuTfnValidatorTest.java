package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuTfnValidatorTest {

    private final AuTfnValidator validator = new AuTfnValidator();

    @Test
    void validTfn_passes() {
        assertTrue(validator.isValid("100000001"));
    }

    @Test
    void invalidChecksum_fails() {
        assertFalse(validator.isValid("100000002"));
    }

    @Test
    void wrongLength_fails() {
        assertFalse(validator.isValid("12345678"));
    }
}
