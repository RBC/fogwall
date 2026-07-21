package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UsBankRoutingValidatorTest {

    private final UsBankRoutingValidator validator = new UsBankRoutingValidator();

    @Test
    void validRoutingNumber_passes() {
        assertTrue(validator.isValid("021000021"));
    }

    @Test
    void invalidChecksum_fails() {
        assertFalse(validator.isValid("021000022"));
    }

    @Test
    void delimitersAreStripped() {
        assertTrue(validator.isValid("021-000-021"));
    }

    @Test
    void wrongLength_fails() {
        assertFalse(validator.isValid("02100002"));
    }
}
