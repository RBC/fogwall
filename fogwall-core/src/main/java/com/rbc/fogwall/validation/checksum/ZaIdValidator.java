package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Year;
import java.util.Set;

/**
 * Structural validation for South African ID numbers, ported from Presidio's {@code ZaIdNumberRecognizer}: valid
 * encoded birth date (rolling century pivot), allowed citizenship digit, allowed legacy race-classification digit, and
 * a left-indexed Luhn checksum variant (South Africa's ID number doubles digits at even index-from-left, not the usual
 * right-to-left Luhn indexing).
 */
public class ZaIdValidator implements PatternValidator {

    private static final Set<Character> ALLOWED_CITIZENSHIP = Set.of('0', '1', '2');
    private static final Set<Character> ALLOWED_LEGACY_RACE = Set.of('8', '9');

    @Override
    public boolean isValid(String matchedText) {
        if (matchedText.length() != 13 || !matchedText.chars().allMatch(Character::isDigit)) {
            return false;
        }
        if (!hasValidBirthDate(matchedText.substring(0, 6))) {
            return false;
        }
        if (!ALLOWED_CITIZENSHIP.contains(matchedText.charAt(10))) {
            return false;
        }
        if (!ALLOWED_LEGACY_RACE.contains(matchedText.charAt(11))) {
            return false;
        }
        return isLuhnValidLeftIndexed(matchedText);
    }

    private static boolean hasValidBirthDate(String datePart) {
        int yearSuffix = Integer.parseInt(datePart.substring(0, 2));
        int month = Integer.parseInt(datePart.substring(2, 4));
        int day = Integer.parseInt(datePart.substring(4, 6));

        int pivot = Year.now().getValue() % 100;
        int century = yearSuffix > pivot ? 1900 : 2000;

        try {
            LocalDate birthDate = LocalDate.of(century + yearSuffix, month, day);
            return !birthDate.isAfter(LocalDate.now());
        } catch (DateTimeException e) {
            return false;
        }
    }

    private static boolean isLuhnValidLeftIndexed(String digits) {
        int parity = digits.length() % 2;
        int checksum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int digit = digits.charAt(i) - '0';
            if (i % 2 == parity) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            checksum += digit;
        }
        return checksum % 10 == 0;
    }
}
