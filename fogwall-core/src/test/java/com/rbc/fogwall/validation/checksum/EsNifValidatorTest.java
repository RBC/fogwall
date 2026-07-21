package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EsNifValidatorTest {

    private final EsNifValidator validator = new EsNifValidator();

    @Test
    void validNif_passes() {
        assertTrue(validator.isValid("0000000T"));
        assertTrue(validator.isValid("12345678Z"));
    }

    @Test
    void wrongCheckLetter_fails() {
        assertFalse(validator.isValid("0000000A"));
        assertFalse(validator.isValid("12345678A"));
    }
}
