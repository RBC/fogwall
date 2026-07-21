package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuAcnValidatorTest {

    private final AuAcnValidator validator = new AuAcnValidator();

    @Test
    void validAcn_passes() {
        assertTrue(validator.isValid("100000002"));
    }

    @Test
    void invalidChecksum_fails() {
        assertFalse(validator.isValid("100000003"));
    }

    @Test
    void delimitersAreStripped() {
        assertTrue(validator.isValid("100 000 002"));
    }

    @Test
    void wrongLength_fails() {
        assertFalse(validator.isValid("10000000"));
    }
}
