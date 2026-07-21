package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuAbnValidatorTest {

    private final AuAbnValidator validator = new AuAbnValidator();

    @Test
    void validAbn_passes() {
        assertTrue(validator.isValid("51824753556"));
    }

    @Test
    void invalidChecksum_fails() {
        assertFalse(validator.isValid("51824753557"));
    }

    @Test
    void delimitersAreStripped() {
        assertTrue(validator.isValid("51 824 753 556"));
    }

    @Test
    void wrongLength_fails() {
        assertFalse(validator.isValid("5182475355"));
    }
}
