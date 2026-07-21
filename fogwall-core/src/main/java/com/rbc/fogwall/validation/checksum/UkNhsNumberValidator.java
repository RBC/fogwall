package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;

/**
 * Modulus 11 checksum for UK NHS numbers, ported from Presidio's {@code NhsRecognizer}: 10 digits, weights 10..2 over
 * the first 9 digits, remainder 11 - (sum mod 11) must equal the 10th digit (remainder 11 maps to check digit 0;
 * remainder 10 is never a valid NHS number).
 */
public class UkNhsNumberValidator implements PatternValidator {

    private static final int[] WEIGHTS = {10, 9, 8, 7, 6, 5, 4, 3, 2};

    @Override
    public boolean isValid(String matchedText) {
        String digits = matchedText.replaceAll("[-\\s]", "");
        if (digits.length() != 10 || !digits.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (digits.charAt(i) - '0') * WEIGHTS[i];
        }
        int checkDigit = 11 - (sum % 11);
        if (checkDigit == 11) {
            checkDigit = 0;
        }
        if (checkDigit == 10) {
            return false;
        }
        return checkDigit == (digits.charAt(9) - '0');
    }
}
