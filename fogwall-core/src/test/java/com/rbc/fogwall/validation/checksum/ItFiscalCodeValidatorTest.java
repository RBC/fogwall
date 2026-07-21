package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ItFiscalCodeValidatorTest {

    private final ItFiscalCodeValidator validator = new ItFiscalCodeValidator();

    @Test
    void validCode_passes() {
        assertTrue(validator.isValid("RSSMRA85M01H501Q"));
    }

    @Test
    void invalidCheckLetter_fails() {
        assertFalse(validator.isValid("RSSMRA85M01H501A"));
    }

    @Test
    void isCaseInsensitive() {
        assertTrue(validator.isValid("rssmra85m01h501q"));
    }

    @Test
    void wrongLength_fails() {
        assertFalse(validator.isValid("RSSMRA85M01H501"));
    }
}
