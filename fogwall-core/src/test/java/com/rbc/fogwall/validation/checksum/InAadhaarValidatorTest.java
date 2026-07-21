package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InAadhaarValidatorTest {

    private final InAadhaarValidator validator = new InAadhaarValidator();

    @Test
    void validAadhaar_passes() {
        assertTrue(validator.isValid("234567890124"));
    }

    @Test
    void invalidChecksum_fails() {
        assertFalse(validator.isValid("234567890125"));
    }

    @Test
    void firstDigitBelowTwo_fails() {
        assertFalse(validator.isValid("134567890124"));
    }

    @Test
    void palindrome_fails() {
        // "234567765432" reads the same forwards and backwards - rejected regardless of checksum validity.
        assertFalse(validator.isValid("234567765432"));
    }

    @Test
    void wrongLength_fails() {
        assertFalse(validator.isValid("2345678901"));
    }

    @Test
    void delimitersAreStripped() {
        assertTrue(validator.isValid("2345 6789 0124"));
    }
}
