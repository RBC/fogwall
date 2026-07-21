package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SePersonnummerValidatorTest {

    private final SePersonnummerValidator validator = new SePersonnummerValidator();

    @Test
    void validPersonnummer_passes() {
        assertTrue(validator.isValid("8001010001"));
    }

    @Test
    void invalidChecksum_fails() {
        assertFalse(validator.isValid("8001010002"));
    }

    @Test
    void invalidMonth_fails() {
        assertFalse(validator.isValid("8033010001"));
    }

    @Test
    void samordningsnummerDayOffset_isAccepted() {
        // Day 61 (= day 1 + 60) is a valid samordningsnummer encoding, not rejected outright.
        assertTrue(validator.isValid("8001610008"));
    }

    @Test
    void tooShort_fails() {
        assertFalse(validator.isValid("800101000"));
    }
}
