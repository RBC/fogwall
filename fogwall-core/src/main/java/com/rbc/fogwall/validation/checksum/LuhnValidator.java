package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;

/**
 * Luhn checksum validator (used by credit card numbers, and by Canada's SIN which has a Luhn check digit). Strips all
 * non-digit characters (delimiters) before validating.
 */
public class LuhnValidator implements PatternValidator {

    @Override
    public boolean isValid(String matchedText) {
        String digits = matchedText.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return false;
        }

        int total = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (doubleIt) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            total += n;
            doubleIt = !doubleIt;
        }
        return total % 10 == 0;
    }
}
