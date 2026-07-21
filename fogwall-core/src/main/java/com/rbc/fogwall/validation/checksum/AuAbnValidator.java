package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;

/**
 * Australian Business Number checksum, ported from Presidio's {@code AuAbnRecognizer}: 11 digits, subtract 1 from the
 * first digit, weighted sum (weights 10,1,3,5,7,9,11,13,15,17,19), valid if divisible by 89.
 */
public class AuAbnValidator implements PatternValidator {

    private static final int[] WEIGHTS = {10, 1, 3, 5, 7, 9, 11, 13, 15, 17, 19};

    @Override
    public boolean isValid(String matchedText) {
        String digits = matchedText.replaceAll("[-\\s]", "");
        if (digits.length() != 11 || !digits.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 11; i++) {
            int value = digits.charAt(i) - '0';
            if (i == 0) {
                value -= 1;
            }
            sum += value * WEIGHTS[i];
        }
        return sum % 89 == 0;
    }
}
