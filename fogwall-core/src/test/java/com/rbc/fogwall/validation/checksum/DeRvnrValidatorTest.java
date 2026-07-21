package com.rbc.fogwall.validation.checksum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeRvnrValidatorTest {

    private final DeRvnrValidator validator = new DeRvnrValidator();

    @Test
    void officialWorkedExample_passes() {
        // Canonical example from Deutsche Rentenversicherung technical documentation.
        assertTrue(validator.isValid("15070649C103"));
    }

    @Test
    void anotherValidChecksum_passes() {
        assertTrue(validator.isValid("15070649C001"));
    }

    @Test
    void invalidChecksum_fails() {
        assertFalse(validator.isValid("15070649C002"));
    }

    @Test
    void impossibleBirthDate_fails() {
        assertFalse(validator.isValid("15139649C103"));
    }

    @Test
    void wrongShape_fails() {
        assertFalse(validator.isValid("not-a-rvnr!!"));
    }
}
