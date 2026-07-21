package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;
import java.time.LocalDate;
import java.util.Map;

/**
 * Structural validation for the Finnish Personal Identity Code (Henkilötunnus), ported from Presidio's
 * {@code FiPersonalIdentityCodeRecognizer}: the separator character encodes the birth century, the encoded date must be
 * valid, and a control character computed modulo 31 from the date + individual number must match.
 */
public class FiPersonalIdentityCodeValidator implements PatternValidator {

    private static final Map<Character, Integer> CENTURY_BY_SEPARATOR = Map.ofEntries(
            Map.entry('+', 1800),
            Map.entry('-', 1900),
            Map.entry('Y', 1900),
            Map.entry('X', 1900),
            Map.entry('W', 1900),
            Map.entry('V', 1900),
            Map.entry('U', 1900),
            Map.entry('A', 2000),
            Map.entry('B', 2000),
            Map.entry('C', 2000),
            Map.entry('D', 2000),
            Map.entry('E', 2000),
            Map.entry('F', 2000));

    private static final String VALID_CONTROL_CHARACTERS = "0123456789ABCDEFHJKLMNPRSTUVWXY";

    @Override
    public boolean isValid(String matchedText) {
        if (matchedText.length() != 11) {
            return false;
        }
        String datePart = matchedText.substring(0, 6);
        int century = CENTURY_BY_SEPARATOR.getOrDefault(matchedText.charAt(6), 2000);

        try {
            LocalDate.of(
                    century + Integer.parseInt(datePart.substring(4, 6)),
                    Integer.parseInt(datePart.substring(2, 4)),
                    Integer.parseInt(datePart.substring(0, 2)));
        } catch (java.time.DateTimeException | NumberFormatException e) {
            return false;
        }

        String individualNumber = matchedText.substring(7, 10);
        char controlCharacter = Character.toUpperCase(matchedText.charAt(matchedText.length() - 1));
        int numberToCheck = Integer.parseInt(datePart + individualNumber);
        return VALID_CONTROL_CHARACTERS.charAt(numberToCheck % 31) == controlCharacter;
    }
}
