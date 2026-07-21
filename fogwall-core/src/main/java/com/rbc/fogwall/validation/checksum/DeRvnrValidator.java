package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;
import java.util.regex.Pattern;

/**
 * Structural validation for German Rentenversicherungsnummer (RVNR/Sozialversicherungsnummer), ported from Presidio's
 * {@code DeSocialSecurityRecognizer}: birth-day/month range checks plus the VKVV &sect;4 cross-sum checksum (letter at
 * position 9 converted to its ordinal, weighted cross-sum of all 12 data digits mod 10).
 */
public class DeRvnrValidator implements PatternValidator {

    private static final Pattern SHAPE = Pattern.compile("^\\d{8}[A-Z]\\d{3}$");
    private static final int[] WEIGHTS = {2, 1, 2, 5, 7, 1, 2, 1, 2, 1, 2, 1};

    @Override
    public boolean isValid(String matchedText) {
        String text = matchedText.toUpperCase().strip();
        if (text.length() != 12 || !SHAPE.matcher(text).matches()) {
            return false;
        }

        int day = Integer.parseInt(text.substring(2, 4));
        int month = Integer.parseInt(text.substring(4, 6));
        if (!((day >= 1 && day <= 31) || (day >= 51 && day <= 81))) {
            return false;
        }
        if (month < 1 || month > 12) {
            return false;
        }

        char letter = text.charAt(8);
        String letterVal = String.format("%02d", letter - 'A' + 1);
        String effective = text.substring(0, 8) + letterVal + text.substring(9, 11);

        int checkDigit = text.charAt(11) - '0';
        int total = 0;
        for (int i = 0; i < effective.length(); i++) {
            int product = (effective.charAt(i) - '0') * WEIGHTS[i];
            total += (product / 10) + (product % 10);
        }
        return (total % 10) == checkDigit;
    }
}
