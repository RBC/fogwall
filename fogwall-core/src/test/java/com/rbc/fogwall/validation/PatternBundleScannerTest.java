package com.rbc.fogwall.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rbc.fogwall.validation.checksum.LuhnValidator;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PatternBundleScannerTest {

    @Test
    void matchWithNoContextRequirement_alwaysFinds() {
        var rule = new PatternRule("Test", Pattern.compile("\\d{4}"), List.of(), 0, null);
        var scanner = new PatternBundleScanner(List.of(new PatternBundle("test", "XX", List.of(rule))));

        List<ContentPatternFinding> findings = scanner.scan("value is 1234 here");

        assertEquals(1, findings.size());
        assertEquals("1234", findings.get(0).matchedText());
    }

    @Test
    void matchWithoutNearbyContextKeyword_isSuppressed() {
        var rule = new PatternRule("CVV", Pattern.compile("\\b\\d{3,4}\\b"), List.of("cvv"), 20, null);
        var scanner = new PatternBundleScanner(List.of(new PatternBundle("test", "XX", List.of(rule))));

        List<ContentPatternFinding> findings = scanner.scan("array index was 123 out of bounds");

        assertTrue(findings.isEmpty());
    }

    @Test
    void matchWithNearbyContextKeyword_isReported() {
        var rule = new PatternRule("CVV", Pattern.compile("\\b\\d{3,4}\\b"), List.of("cvv"), 20, null);
        var scanner = new PatternBundleScanner(List.of(new PatternBundle("test", "XX", List.of(rule))));

        List<ContentPatternFinding> findings = scanner.scan("cvv: 123");

        assertEquals(1, findings.size());
        assertEquals("123", findings.get(0).matchedText());
    }

    @Test
    void contextKeywordOutsideWindow_isSuppressed() {
        var rule = new PatternRule("CVV", Pattern.compile("\\b\\d{3,4}\\b"), List.of("cvv"), 5, null);
        var scanner = new PatternBundleScanner(List.of(new PatternBundle("test", "XX", List.of(rule))));

        List<ContentPatternFinding> findings = scanner.scan("cvv value is way over there ................ 123");

        assertTrue(findings.isEmpty());
    }

    @Test
    void failingValidator_suppressesFinding() {
        var rule = new PatternRule("Checksum", Pattern.compile("\\d{11}"), List.of(), 0, new LuhnValidator());
        var scanner = new PatternBundleScanner(List.of(new PatternBundle("test", "XX", List.of(rule))));

        // 79927398710 is the same shape but Luhn-invalid (see LuhnValidatorTest); 79927398713 is valid.
        assertTrue(scanner.scan("79927398710").isEmpty());
        assertEquals(1, scanner.scan("79927398713").size());
    }

    @Test
    void blankOrNullText_returnsEmpty() {
        var rule = new PatternRule("Test", Pattern.compile("\\d+"), List.of(), 0, null);
        var scanner = new PatternBundleScanner(List.of(new PatternBundle("test", "XX", List.of(rule))));

        assertTrue(scanner.scan(null).isEmpty());
        assertTrue(scanner.scan("").isEmpty());
        assertTrue(scanner.scan("   ").isEmpty());
    }
}
