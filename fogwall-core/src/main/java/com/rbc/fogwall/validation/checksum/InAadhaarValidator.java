package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;

/**
 * Structural validation for Indian Aadhaar numbers, ported from Presidio's {@code InAadhaarRecognizer}: 12 digits,
 * first digit &gt;= 2, not a palindrome, and Verhoeff-checksum valid.
 */
public class InAadhaarValidator implements PatternValidator {

    @Override
    public boolean isValid(String matchedText) {
        String digits = matchedText.replaceAll("[-\\s:]", "");
        if (digits.length() != 12 || !digits.chars().allMatch(Character::isDigit)) {
            return false;
        }
        if (digits.charAt(0) - '0' < 2) {
            return false;
        }
        if (new StringBuilder(digits).reverse().toString().equals(digits)) {
            return false;
        }
        return Verhoeff.isValid(digits);
    }
}
