package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ZaIdValidatorTest {

    private final ZaIdValidator validator = new ZaIdValidator();

    @Test
    void validId_passes() {
        assertTrue(validator.isValid("8001015019086"));
    }

    @Test
    void invalidChecksum_fails() {
        assertFalse(validator.isValid("8001015019087"));
    }

    @Test
    void invalidBirthDate_fails() {
        // Same as the valid vector but with month "33" (positions 2-3) instead of "01" - no such month.
        assertFalse(validator.isValid("8033015019086"));
    }

    @Test
    void disallowedCitizenshipDigit_fails() {
        // Same as the valid vector but with citizenship digit (position 10) "5" instead of "0" - not in {0,1,2}.
        assertFalse(validator.isValid("8001015019586"));
    }
}
