package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BitcoinAddressValidatorTest {

    private final BitcoinAddressValidator validator = new BitcoinAddressValidator();

    @Test
    void validGenesisAddress_passes() {
        assertTrue(validator.isValid("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"));
    }

    @Test
    void validP2shAddress_passes() {
        assertTrue(validator.isValid("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy"));
    }

    @Test
    void invalidChecksum_fails() {
        assertFalse(validator.isValid("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNb"));
    }

    @Test
    void notBase58_fails() {
        assertFalse(validator.isValid("not-a-bitcoin-address"));
    }

    @Test
    void wrongDecodedLength_fails() {
        assertFalse(validator.isValid("1A1zP1eP5QGefi2DMPTfTL5SLmv7Div"));
    }
}
