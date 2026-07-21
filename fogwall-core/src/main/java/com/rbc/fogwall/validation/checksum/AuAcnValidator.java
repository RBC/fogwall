package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;

/**
 * Australian Company Number checksum: 9 digits, weighted sum (weights 8,7,6,5,4,3,2,1) over the first 8 digits,
 * complement = 10 - (sum mod 10) (a complement of 10 maps to check digit 0), must equal the 9th digit.
 */
public class AuAcnValidator implements PatternValidator {

    private static final int[] WEIGHTS = {8, 7, 6, 5, 4, 3, 2, 1};

    @Override
    public boolean isValid(String matchedText) {
        String digits = matchedText.replaceAll("[-\\s]", "");
        if (digits.length() != 9 || !digits.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            sum += (digits.charAt(i) - '0') * WEIGHTS[i];
        }
        int complement = 10 - (sum % 10);
        if (complement == 10) {
            complement = 0;
        }
        return complement == (digits.charAt(8) - '0');
    }
}
