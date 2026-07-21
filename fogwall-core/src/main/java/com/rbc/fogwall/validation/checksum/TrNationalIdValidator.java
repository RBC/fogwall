package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;

/**
 * Structural validation for the Turkish National ID Number (TC Kimlik No), ported from Presidio's
 * {@code TrNationalIdRecognizer}: 11 digits, first digit non-zero, and the official NVI two-stage checksum.
 */
public class TrNationalIdValidator implements PatternValidator {

    @Override
    public boolean isValid(String matchedText) {
        if (matchedText.length() != 11 || !matchedText.chars().allMatch(Character::isDigit)) {
            return false;
        }
        if (matchedText.charAt(0) == '0') {
            return false;
        }

        int[] digits = matchedText.chars().map(c -> c - '0').toArray();

        int oddSum = digits[0] + digits[2] + digits[4] + digits[6] + digits[8];
        int evenSum = digits[1] + digits[3] + digits[5] + digits[7];
        int tenth = Math.floorMod(oddSum * 7 - evenSum, 10);
        if (tenth != digits[9]) {
            return false;
        }

        int sumFirstTen = 0;
        for (int i = 0; i < 10; i++) {
            sumFirstTen += digits[i];
        }
        int eleventh = sumFirstTen % 10;
        return eleventh == digits[10];
    }
}
