package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;
import java.util.Set;

/**
 * Structural validation for US Social Security Numbers, ported from Presidio's {@code UsSsnRecognizer}. An SSN has no
 * checksum digit, so this rejects known-invalid shapes rather than confirming validity: mismatched delimiters,
 * all-same-digit strings, all-zero group segments, area-number prefixes the SSA never issues ({@code 000}/{@code 666}),
 * and canonical placeholder SSNs that show up in examples/test fixtures.
 */
public class UsSsnStructuralValidator implements PatternValidator {

    private static final Set<String> PLACEHOLDER_SSNS = Set.of("123456789", "987654320", "078051120");

    @Override
    public boolean isValid(String matchedText) {
        long distinctDelimiters = matchedText
                .chars()
                .filter(c -> c == '.' || c == '-' || c == ' ')
                .distinct()
                .count();
        if (distinctDelimiters > 1) {
            return false;
        }

        String digits = matchedText.replaceAll("\\D", "");
        if (digits.length() != 9) {
            return false;
        }

        boolean allSameDigit = digits.chars().distinct().count() == 1;
        if (allSameDigit) {
            return false;
        }

        String areaNumber = digits.substring(0, 3);
        String groupNumber = digits.substring(3, 5);
        String serialNumber = digits.substring(5);

        if (groupNumber.equals("00") || serialNumber.equals("0000")) {
            return false;
        }
        if (areaNumber.equals("000") || areaNumber.equals("666")) {
            return false;
        }
        return !PLACEHOLDER_SSNS.contains(digits);
    }
}
