package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LuhnValidatorTest {

    private final LuhnValidator validator = new LuhnValidator();

    @Test
    void validLuhnString_passes() {
        // Textbook Luhn example used throughout algorithm references - checksum-valid.
        assertTrue(validator.isValid("79927398713"));
    }

    @Test
    void lastDigitChanged_fails() {
        assertFalse(validator.isValid("79927398710"));
        assertFalse(validator.isValid("79927398711"));
        assertFalse(validator.isValid("79927398712"));
    }

    @Test
    void delimitersAreStrippedBeforeValidation() {
        assertTrue(validator.isValid("799-273-98713"));
        assertTrue(validator.isValid("799 273 98713"));
    }

    @Test
    void validCanadianSinShapedString_passes() {
        // Constructed 9-digit string (not a real SIN) that happens to be Luhn-valid, to exercise the shape CA_SIN
        // rules actually match against.
        assertTrue(validator.isValid("046454286"));
        assertFalse(validator.isValid("046454287"));
    }

    @Test
    void emptyOrNonDigitOnly_fails() {
        assertFalse(validator.isValid(""));
        assertFalse(validator.isValid("---"));
    }
}
