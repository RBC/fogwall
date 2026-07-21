package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IbanValidatorTest {

    private final IbanValidator validator = new IbanValidator();

    @Test
    void validGbIban_passes() {
        assertTrue(validator.isValid("GB82WEST12345698765432"));
    }

    @Test
    void validDeIban_passes() {
        assertTrue(validator.isValid("DE89370400440532013000"));
    }

    @Test
    void invalidChecksum_fails() {
        assertFalse(validator.isValid("GB82WEST12345698765433"));
    }

    @Test
    void delimitersAreStripped() {
        assertTrue(validator.isValid("GB82 WEST 1234 5698 7654 32"));
    }

    @Test
    void lowercaseIsAccepted() {
        assertTrue(validator.isValid("gb82west12345698765432"));
    }

    @Test
    void wrongShape_fails() {
        assertFalse(validator.isValid("NOTANIBAN"));
    }
}
