package com.rbc.fogwall.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BuiltInPatternBundleSourceTest {

    @Test
    void loadsAllBuiltInBundles() {
        List<PatternBundle> bundles = BuiltInPatternBundleSource.loadAll();

        assertEquals(BuiltInPatternBundleSource.BUNDLE_NAMES.size(), bundles.size());
        Map<String, PatternBundle> byName = bundles.stream().collect(Collectors.toMap(PatternBundle::name, b -> b));
        assertTrue(byName.containsKey("national-id-ca"));
        assertTrue(byName.containsKey("national-id-us"));
        assertTrue(byName.containsKey("national-id-gb"));
    }

    @Test
    void canadaBundle_findsSinWithContextAndValidLuhn() {
        var scanner = new PatternBundleScanner(BuiltInPatternBundleSource.loadAll().stream()
                .filter(b -> b.name().equals("national-id-ca"))
                .toList());

        // 123456782 is Luhn-valid and starts with a digit the national-id-ca.json regex accepts (1-7 or 9; SINs
        // starting with 0 or 8 are reserved and excluded by the regex itself, separately from the Luhn check).
        List<ContentPatternFinding> found = scanner.scan("employee sin: 123 456 782");
        assertEquals(1, found.size());
        assertEquals("Social Insurance Number", found.get(0).dataType());

        assertTrue(scanner.scan("random number: 123 456 782").isEmpty(), "no context keyword nearby");
        assertTrue(scanner.scan("sin: 123 456 783").isEmpty(), "Luhn-invalid");
    }

    @Test
    void usBundle_findsSsnWithContextAndStructuralValidation() {
        var scanner = new PatternBundleScanner(BuiltInPatternBundleSource.loadAll().stream()
                .filter(b -> b.name().equals("national-id-us"))
                .toList());

        assertEquals(1, scanner.scan("ssn: 212-96-7431").size());
        assertTrue(scanner.scan("phone: 212-96-7431").isEmpty(), "no context keyword nearby");
        assertTrue(scanner.scan("ssn: 123-45-6789").isEmpty(), "canonical placeholder SSN rejected");
    }

    @Test
    void ukBundle_findsNinoWithContext() {
        var scanner = new PatternBundleScanner(BuiltInPatternBundleSource.loadAll().stream()
                .filter(b -> b.name().equals("national-id-gb"))
                .toList());

        assertEquals(1, scanner.scan("national insurance number: AB123456C").size());
        assertTrue(scanner.scan("random code: AB123456C").isEmpty(), "no context keyword nearby");
        assertTrue(scanner.scan("nino: GB123456C").isEmpty(), "GB prefix is excluded");
    }

    @Test
    void auBundle_findsTfnWithContextAndValidChecksum() {
        var scanner = new PatternBundleScanner(BuiltInPatternBundleSource.loadAll().stream()
                .filter(b -> b.name().equals("national-id-au"))
                .toList());

        assertEquals(1, scanner.scan("tax file number: 100 000 001").size());
        assertTrue(scanner.scan("random number: 100 000 001").isEmpty(), "no context keyword nearby");
        assertTrue(scanner.scan("tfn: 100 000 002").isEmpty(), "invalid checksum");
    }

    @Test
    void itBundle_findsFiscalCodeWithContextAndValidChecksum() {
        var scanner = new PatternBundleScanner(BuiltInPatternBundleSource.loadAll().stream()
                .filter(b -> b.name().equals("national-id-it"))
                .toList());

        assertEquals(1, scanner.scan("codice fiscale: RSSMRA85M01H501Q").size());
        assertTrue(scanner.scan("random code: RSSMRA85M01H501Q").isEmpty(), "no context keyword nearby");
        assertTrue(scanner.scan("codice fiscale: RSSMRA85M01H501A").isEmpty(), "invalid check letter");
    }

    @Test
    void sgAndPhBundles_haveNoValidator_matchOnContextAlone() {
        var scanner = new PatternBundleScanner(BuiltInPatternBundleSource.loadAll().stream()
                .filter(b -> b.name().equals("national-id-sg") || b.name().equals("national-id-ph"))
                .toList());

        assertEquals(1, scanner.scan("nric: S2740116C").size());
        assertEquals(1, scanner.scan("umid number: 1234-5678901-2").size());
    }

    @Test
    void gbBundle_findsNhsNumberWithContextAndValidChecksum() {
        var scanner = new PatternBundleScanner(BuiltInPatternBundleSource.loadAll().stream()
                .filter(b -> b.name().equals("national-id-gb"))
                .toList());

        assertEquals(1, scanner.scan("nhs number: 943 476 5919").size());
        assertTrue(scanner.scan("random number: 943 476 5919").isEmpty(), "no context keyword nearby");
        assertTrue(scanner.scan("nhs number: 943 476 5918").isEmpty(), "invalid checksum");
    }

    @Test
    void auBundle_findsAbnAndAcnWithContextAndValidChecksum() {
        var scanner = new PatternBundleScanner(BuiltInPatternBundleSource.loadAll().stream()
                .filter(b -> b.name().equals("national-id-au"))
                .toList());

        assertEquals(1, scanner.scan("abn: 51 824 753 556").size());
        assertTrue(scanner.scan("abn: 51 824 753 557").isEmpty(), "invalid ABN checksum");

        assertEquals(1, scanner.scan("acn: 100 000 002").size());
        assertTrue(scanner.scan("acn: 100 000 003").isEmpty(), "invalid ACN checksum");
    }

    @Test
    void genericIbanBundle_findsIbanWithContextAndValidChecksum() {
        var scanner = new PatternBundleScanner(BuiltInPatternBundleSource.loadAll().stream()
                .filter(b -> b.name().equals("generic-iban"))
                .toList());

        assertEquals(1, scanner.scan("iban: GB82WEST12345698765432").size());
        assertTrue(scanner.scan("random code: GB82WEST12345698765432").isEmpty(), "no context keyword nearby");
        assertTrue(scanner.scan("iban: GB82WEST12345698765433").isEmpty(), "invalid checksum");
    }

    @Test
    void genericCreditCardBundle_findsCardNumberWithContextAndValidLuhn() {
        var scanner = new PatternBundleScanner(BuiltInPatternBundleSource.loadAll().stream()
                .filter(b -> b.name().equals("generic-credit-card"))
                .toList());

        // 4111111111111111 is a well-known Luhn-valid test Visa number.
        assertEquals(1, scanner.scan("credit card: 4111111111111111").size());
        assertTrue(scanner.scan("random number: 4111111111111111").isEmpty(), "no context keyword nearby");
        assertTrue(scanner.scan("credit card: 4111111111111112").isEmpty(), "Luhn-invalid");
    }

    @Test
    void genericCryptoWalletBundle_findsAllThreeAddressFormats() {
        var scanner = new PatternBundleScanner(BuiltInPatternBundleSource.loadAll().stream()
                .filter(b -> b.name().equals("generic-crypto-wallet"))
                .toList());

        assertEquals(
                1,
                scanner.scan("bitcoin address: 1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
                        .size());
        assertEquals(
                1,
                scanner.scan("wallet address: bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")
                        .size());
        assertEquals(
                1,
                scanner.scan("metamask wallet: 0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed")
                        .size());
        assertTrue(
                scanner.scan("random string: 1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
                        .isEmpty(),
                "no context keyword nearby");
    }

    @Test
    void genericUsBankRoutingBundle_findsRoutingNumberWithContextAndValidChecksum() {
        var scanner = new PatternBundleScanner(BuiltInPatternBundleSource.loadAll().stream()
                .filter(b -> b.name().equals("generic-us-bank-routing"))
                .toList());

        assertEquals(1, scanner.scan("routing number: 021000021").size());
        assertTrue(scanner.scan("random number: 021000021").isEmpty(), "no context keyword nearby");
        assertTrue(scanner.scan("routing number: 021000022").isEmpty(), "invalid checksum");
    }

    @Test
    void nationalIdAllGeosAlias_expandsToEveryNationalIdBundle() {
        List<String> expanded =
                BuiltInPatternBundleSource.expandAlias(BuiltInPatternBundleSource.ALIAS_NATIONAL_ID_ALL_GEOS);

        assertTrue(expanded.stream().allMatch(n -> n.startsWith("national-id-")));
        assertTrue(expanded.contains("national-id-us"));
        assertEquals(17, expanded.size());
    }

    @Test
    void genericAllAlias_expandsToEveryGenericBundle() {
        List<String> expanded = BuiltInPatternBundleSource.expandAlias(BuiltInPatternBundleSource.ALIAS_GENERIC_ALL);

        assertTrue(expanded.stream().allMatch(n -> n.startsWith("generic-")));
        assertTrue(expanded.contains("generic-iban"));
        assertEquals(4, expanded.size());
    }

    @Test
    void concreteBundleName_expandsToItself() {
        assertEquals(List.of("national-id-us"), BuiltInPatternBundleSource.expandAlias("national-id-us"));
    }

    @Test
    void unregisteredValidatorName_throwsAtLoadTime() {
        // Sanity check that a typo'd validator name fails loudly rather than silently passing through.
        var badRule = new PatternBundleJson.PatternRuleJson("Test", "\\d+", List.of(), 0, "not-a-real-validator");
        assertFalse(PatternValidators.byName(badRule.validator()).isPresent());
    }
}
