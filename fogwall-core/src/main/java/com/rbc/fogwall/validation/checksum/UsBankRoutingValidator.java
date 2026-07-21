package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;

/**
 * ABA routing transit number checksum - the standard weighted-sum algorithm published by the American Bankers
 * Association (not sourced from Presidio): 9 digits, weights 3-7-1 repeating across the three groups, sum must be
 * divisible by 10.
 */
public class UsBankRoutingValidator implements PatternValidator {

    @Override
    public boolean isValid(String matchedText) {
        String digits = matchedText.replaceAll("[-\\s]", "");
        if (digits.length() != 9 || !digits.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 9; i += 3) {
            sum += 3 * (digits.charAt(i) - '0');
            sum += 7 * (digits.charAt(i + 1) - '0');
            sum += (digits.charAt(i + 2) - '0');
        }
        return sum % 10 == 0;
    }
}
