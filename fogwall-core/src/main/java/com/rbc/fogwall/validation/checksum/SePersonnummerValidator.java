package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;

/**
 * Structural validation for Swedish Personnummer, ported from Presidio's {@code SePersonnummerRecognizer}: uses the
 * last 10 digits, validates month/day ranges (accounting for samordningsnummer, which adds 60 to the day), then applies
 * a Luhn checksum where the check digit is the trailing digit.
 */
public class SePersonnummerValidator implements PatternValidator {

    @Override
    public boolean isValid(String matchedText) {
        String allDigits = matchedText.replaceAll("\\D", "");
        if (allDigits.length() < 10) {
            return false;
        }
        String num = allDigits.substring(allDigits.length() - 10);

        if (!hasValidDate(num)) {
            return false;
        }
        return isLuhnValid(num);
    }

    private static boolean hasValidDate(String num) {
        int month = Integer.parseInt(num.substring(2, 4));
        int day = Integer.parseInt(num.substring(4, 6));
        if (day >= 61) {
            day -= 60;
        }
        return month >= 1 && month <= 12 && day >= 1 && day <= 31;
    }

    private static boolean isLuhnValid(String num) {
        int checksum = num.charAt(9) - '0';
        int luhnSum = 0;
        String firstNine = num.substring(0, 9);
        for (int i = 0; i < firstNine.length(); i++) {
            int digit = firstNine.charAt(firstNine.length() - 1 - i) - '0';
            if (i % 2 == 0) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            luhnSum += digit;
        }
        return (luhnSum + checksum) % 10 == 0;
    }
}
