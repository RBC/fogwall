package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;

/**
 * Structural validation for Spanish NIF numbers, ported from Presidio's {@code EsNifRecognizer}: the numeric part
 * modulo 23 indexes into a fixed letter table, which must match the trailing check letter.
 */
public class EsNifValidator implements PatternValidator {

    private static final String LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";

    @Override
    public boolean isValid(String matchedText) {
        String text = matchedText.replaceAll("[-\\s]", "").toUpperCase();
        if (text.isEmpty()) {
            return false;
        }
        char letter = text.charAt(text.length() - 1);
        String digits = text.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return false;
        }
        int number = Integer.parseInt(digits);
        return letter == LETTERS.charAt(number % 23);
    }
}
