package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;
import java.util.Map;

/**
 * Structural validation for the Italian Fiscal Code (Codice Fiscale), ported from Presidio's
 * {@code ItFiscalCodeRecognizer}: odd- and even-position characters are mapped through fixed value tables, summed, and
 * taken modulo 26 to index into a check-letter table, which must match the code's trailing character.
 */
public class ItFiscalCodeValidator implements PatternValidator {

    private static final Map<Character, Integer> ODD_VALUES = Map.ofEntries(
            Map.entry('0', 1),
            Map.entry('1', 0),
            Map.entry('2', 5),
            Map.entry('3', 7),
            Map.entry('4', 9),
            Map.entry('5', 13),
            Map.entry('6', 15),
            Map.entry('7', 17),
            Map.entry('8', 19),
            Map.entry('9', 21),
            Map.entry('A', 1),
            Map.entry('B', 0),
            Map.entry('C', 5),
            Map.entry('D', 7),
            Map.entry('E', 9),
            Map.entry('F', 13),
            Map.entry('G', 15),
            Map.entry('H', 17),
            Map.entry('I', 19),
            Map.entry('J', 21),
            Map.entry('K', 2),
            Map.entry('L', 4),
            Map.entry('M', 18),
            Map.entry('N', 20),
            Map.entry('O', 11),
            Map.entry('P', 3),
            Map.entry('Q', 6),
            Map.entry('R', 8),
            Map.entry('S', 12),
            Map.entry('T', 14),
            Map.entry('U', 16),
            Map.entry('V', 10),
            Map.entry('W', 22),
            Map.entry('X', 25),
            Map.entry('Y', 24),
            Map.entry('Z', 23));

    private static final Map<Character, Integer> EVEN_VALUES = Map.ofEntries(
            Map.entry('0', 0),
            Map.entry('1', 1),
            Map.entry('2', 2),
            Map.entry('3', 3),
            Map.entry('4', 4),
            Map.entry('5', 5),
            Map.entry('6', 6),
            Map.entry('7', 7),
            Map.entry('8', 8),
            Map.entry('9', 9),
            Map.entry('A', 0),
            Map.entry('B', 1),
            Map.entry('C', 2),
            Map.entry('D', 3),
            Map.entry('E', 4),
            Map.entry('F', 5),
            Map.entry('G', 6),
            Map.entry('H', 7),
            Map.entry('I', 8),
            Map.entry('J', 9),
            Map.entry('K', 10),
            Map.entry('L', 11),
            Map.entry('M', 12),
            Map.entry('N', 13),
            Map.entry('O', 14),
            Map.entry('P', 15),
            Map.entry('Q', 16),
            Map.entry('R', 17),
            Map.entry('S', 18),
            Map.entry('T', 19),
            Map.entry('U', 20),
            Map.entry('V', 21),
            Map.entry('W', 22),
            Map.entry('X', 23),
            Map.entry('Y', 24),
            Map.entry('Z', 25));

    private static final String CHECK_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    @Override
    public boolean isValid(String matchedText) {
        String text = matchedText.toUpperCase();
        if (text.length() != 16) {
            return false;
        }
        char control = text.charAt(text.length() - 1);
        String toValidate = text.substring(0, text.length() - 1);

        int oddSum = 0;
        int evenSum = 0;
        for (int i = 0; i < toValidate.length(); i++) {
            char c = toValidate.charAt(i);
            Integer oddVal = ODD_VALUES.get(c);
            Integer evenVal = EVEN_VALUES.get(c);
            if (oddVal == null || evenVal == null) {
                return false;
            }
            // Presidio indexes 0-based: text_to_validate[0::2] (0,2,4,...) uses the "odd" map despite the name -
            // it's "odd position in 1-based counting", i.e. even Java index.
            if (i % 2 == 0) {
                oddSum += oddVal;
            } else {
                evenSum += evenVal;
            }
        }
        char checkValue = CHECK_LETTERS.charAt((oddSum + evenSum) % 26);
        return checkValue == control;
    }
}
