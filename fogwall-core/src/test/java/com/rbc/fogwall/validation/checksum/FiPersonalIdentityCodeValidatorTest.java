package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FiPersonalIdentityCodeValidatorTest {

    private final FiPersonalIdentityCodeValidator validator = new FiPersonalIdentityCodeValidator();

    @Test
    void validCode_passes() {
        assertTrue(validator.isValid("010180-0003"));
    }

    @Test
    void invalidControlCharacter_fails() {
        assertFalse(validator.isValid("010180-0004"));
    }

    @Test
    void invalidDate_fails() {
        assertFalse(validator.isValid("310280-0003"));
    }

    @Test
    void wrongLength_fails() {
        assertFalse(validator.isValid("01018-0003"));
    }
}
