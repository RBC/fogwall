package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;

/**
 * Structural validation for the Thai National ID Number (TNIN), ported from Presidio's {@code ThTninRecognizer}: 13
 * digits, weighted-sum checksum (weights 13 down to 2) modulo 11, with a special case near zero.
 */
public class ThTninValidator implements PatternValidator {

    @Override
    public boolean isValid(String matchedText) {
        if (matchedText.length() != 13 || !matchedText.chars().allMatch(Character::isDigit)) {
            return false;
        }

        int totalSum = 0;
        int weight = 13;
        for (int i = 0; i < 12; i++) {
            totalSum += weight * (matchedText.charAt(i) - '0');
            weight--;
        }

        int x = totalSum % 11;
        int expectedCheckDigit = x <= 1 ? 1 - x : 11 - x;
        int actualCheckDigit = matchedText.charAt(12) - '0';
        return expectedCheckDigit == actualCheckDigit;
    }
}
