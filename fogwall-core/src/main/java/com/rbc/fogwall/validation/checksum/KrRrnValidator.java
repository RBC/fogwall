package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;

/**
 * Structural validation for the Korean Resident Registration Number, ported from Presidio's {@code KrRrnRecognizer}: 13
 * digits, region code in range, and a weighted-sum checksum modulo 11 then modulo 10. Presidio treats this as "unknown"
 * (not a hard reject) for RRNs issued after October 2020, where the checksum no longer applies - fogwall has no
 * three-state confidence model, so an RRN that fails this check is simply not reported, same as any other failed
 * structural validation.
 */
public class KrRrnValidator implements PatternValidator {

    private static final int[] WEIGHTS = {2, 3, 4, 5, 6, 7, 8, 9, 2, 3, 4, 5};

    @Override
    public boolean isValid(String matchedText) {
        String digits = matchedText.replaceAll("-", "");
        if (digits.length() != 13 || !digits.chars().allMatch(Character::isDigit)) {
            return false;
        }

        int regionCode = Integer.parseInt(digits.substring(7, 9));
        if (regionCode < 0 || regionCode > 95) {
            return false;
        }

        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (digits.charAt(i) - '0') * WEIGHTS[i];
        }
        int checksum = (11 - (sum % 11)) % 10;
        return checksum == (digits.charAt(12) - '0');
    }
}
