package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UsSsnStructuralValidatorTest {

    private final UsSsnStructuralValidator validator = new UsSsnStructuralValidator();

    @Test
    void plausibleShape_passes() {
        assertTrue(validator.isValid("212-96-7431"));
        assertTrue(validator.isValid("212 96 7431"));
        assertTrue(validator.isValid("212.96.7431"));
    }

    @Test
    void mismatchedDelimiters_fails() {
        assertFalse(validator.isValid("212-96 7431"));
        assertFalse(validator.isValid("212.96-7431"));
    }

    @Test
    void allSameDigit_fails() {
        assertFalse(validator.isValid("111-11-1111"));
    }

    @Test
    void groupOrSerialAllZero_fails() {
        assertFalse(validator.isValid("212-00-7431"));
        assertFalse(validator.isValid("212-96-0000"));
    }

    @Test
    void reservedAreaPrefix_fails() {
        assertFalse(validator.isValid("000-12-3456"));
        assertFalse(validator.isValid("666-12-3456"));
    }

    @Test
    void canonicalPlaceholderSsns_fail() {
        assertFalse(validator.isValid("123-45-6789"));
        assertFalse(validator.isValid("987-65-4320"));
        assertFalse(validator.isValid("078-05-1120"));
    }
}
