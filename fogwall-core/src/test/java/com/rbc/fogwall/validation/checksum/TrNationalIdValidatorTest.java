package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrNationalIdValidatorTest {

    private final TrNationalIdValidator validator = new TrNationalIdValidator();

    @Test
    void validId_passes() {
        assertTrue(validator.isValid("10000000078"));
    }

    @Test
    void invalidChecksum_fails() {
        assertFalse(validator.isValid("10000000079"));
    }

    @Test
    void leadingZero_fails() {
        assertFalse(validator.isValid("00000000078"));
    }

    @Test
    void wrongLength_fails() {
        assertFalse(validator.isValid("1000000007"));
    }
}
