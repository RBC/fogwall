package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;

/**
 * Structural validation for Australian Tax File Numbers, ported from Presidio's {@code AuTfnRecognizer}: 9 digits,
 * weighted-sum checksum modulo 11.
 */
public class AuTfnValidator implements PatternValidator {

    private static final int[] WEIGHTS = {1, 4, 3, 7, 5, 8, 6, 9, 10};

    @Override
    public boolean isValid(String matchedText) {
        String digits = matchedText.replaceAll("[-\\s]", "");
        if (digits.length() != 9 || !digits.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (digits.charAt(i) - '0') * WEIGHTS[i];
        }
        return sum % 11 == 0;
    }
}
