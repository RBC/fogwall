package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Eip55AddressValidatorTest {

    private final Eip55AddressValidator validator = new Eip55AddressValidator();

    @Test
    void validChecksummedAddress_passes() {
        assertTrue(validator.isValid("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"));
    }

    @Test
    void wrongCasing_fails() {
        assertFalse(validator.isValid("0x5aAEb6053F3E94C9b9A09f33669435E7Ef1BeAed"));
    }

    @Test
    void allLowercase_hasNoChecksumSignal_fails() {
        assertFalse(validator.isValid("0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed"));
    }

    @Test
    void allUppercase_hasNoChecksumSignal_fails() {
        assertFalse(validator.isValid("0x5AAEB6053F3E94C9B9A09F33669435E7EF1BEAED"));
    }

    @Test
    void missingPrefix_fails() {
        assertFalse(validator.isValid("5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"));
    }

    @Test
    void wrongLength_fails() {
        assertFalse(validator.isValid("0x5aAeb6053F3E94C9"));
    }
}
