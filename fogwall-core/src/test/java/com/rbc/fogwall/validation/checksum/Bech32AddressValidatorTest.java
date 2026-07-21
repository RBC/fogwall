package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Bech32AddressValidatorTest {

    private final Bech32AddressValidator validator = new Bech32AddressValidator();

    @Test
    void validSegwitAddress_passes() {
        assertTrue(validator.isValid("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"));
    }

    @Test
    void validTaprootAddress_passes() {
        assertTrue(validator.isValid("bc1p5cyxnuxmeuwuvkwfem96lqzszd02n6xdcjrs20cac6yqjjwudpxqkedrcr"));
    }

    @Test
    void invalidChecksum_fails() {
        assertFalse(validator.isValid("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t5"));
    }

    @Test
    void uppercaseHrp_isAccepted() {
        assertTrue(validator.isValid("BC1QW508D6QEJXTDG4Y5R3ZARVARY0C5XW7KV8F3T4"));
    }

    @Test
    void notBech32_fails() {
        assertFalse(validator.isValid("not-a-bech32-address"));
    }
}
