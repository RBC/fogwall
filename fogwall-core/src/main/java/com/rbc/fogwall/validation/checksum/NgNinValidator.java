package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;

/**
 * Structural validation for Nigerian National Identification Numbers, ported from Presidio's {@code NgNinRecognizer}:
 * 11 digits, Verhoeff-checksum valid.
 */
public class NgNinValidator implements PatternValidator {

    @Override
    public boolean isValid(String matchedText) {
        if (matchedText.length() != 11 || !matchedText.chars().allMatch(Character::isDigit)) {
            return false;
        }
        return Verhoeff.isValid(matchedText);
    }
}
