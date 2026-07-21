package com.rbc.fogwall.validation;

import com.rbc.fogwall.validation.checksum.AuAbnValidator;
import com.rbc.fogwall.validation.checksum.AuAcnValidator;
import com.rbc.fogwall.validation.checksum.AuTfnValidator;
import com.rbc.fogwall.validation.checksum.Bech32AddressValidator;
import com.rbc.fogwall.validation.checksum.BitcoinAddressValidator;
import com.rbc.fogwall.validation.checksum.DeRvnrValidator;
import com.rbc.fogwall.validation.checksum.Eip55AddressValidator;
import com.rbc.fogwall.validation.checksum.EsNifValidator;
import com.rbc.fogwall.validation.checksum.FiPersonalIdentityCodeValidator;
import com.rbc.fogwall.validation.checksum.IbanValidator;
import com.rbc.fogwall.validation.checksum.InAadhaarValidator;
import com.rbc.fogwall.validation.checksum.ItFiscalCodeValidator;
import com.rbc.fogwall.validation.checksum.KrRrnValidator;
import com.rbc.fogwall.validation.checksum.LuhnValidator;
import com.rbc.fogwall.validation.checksum.NgNinValidator;
import com.rbc.fogwall.validation.checksum.SePersonnummerValidator;
import com.rbc.fogwall.validation.checksum.ThTninValidator;
import com.rbc.fogwall.validation.checksum.TrNationalIdValidator;
import com.rbc.fogwall.validation.checksum.UkNhsNumberValidator;
import com.rbc.fogwall.validation.checksum.UsBankRoutingValidator;
import com.rbc.fogwall.validation.checksum.UsSsnStructuralValidator;
import com.rbc.fogwall.validation.checksum.ZaIdValidator;
import java.util.Map;
import java.util.Optional;

/** Registry resolving the {@code validator} name on a {@link PatternRule} to a {@link PatternValidator} instance. */
public final class PatternValidators {

    private static final Map<String, PatternValidator> REGISTRY = Map.ofEntries(
            Map.entry("luhn", new LuhnValidator()),
            Map.entry("us-ssn-structural", new UsSsnStructuralValidator()),
            Map.entry("au-tfn", new AuTfnValidator()),
            Map.entry("de-rvnr", new DeRvnrValidator()),
            Map.entry("in-aadhaar", new InAadhaarValidator()),
            Map.entry("ng-nin", new NgNinValidator()),
            Map.entry("za-id", new ZaIdValidator()),
            Map.entry("es-nif", new EsNifValidator()),
            Map.entry("se-personnummer", new SePersonnummerValidator()),
            Map.entry("tr-national-id", new TrNationalIdValidator()),
            Map.entry("fi-personal-identity-code", new FiPersonalIdentityCodeValidator()),
            Map.entry("it-fiscal-code", new ItFiscalCodeValidator()),
            Map.entry("kr-rrn", new KrRrnValidator()),
            Map.entry("th-tnin", new ThTninValidator()),
            Map.entry("uk-nhs-number", new UkNhsNumberValidator()),
            Map.entry("au-abn", new AuAbnValidator()),
            Map.entry("au-acn", new AuAcnValidator()),
            Map.entry("iban", new IbanValidator()),
            Map.entry("btc-address", new BitcoinAddressValidator()),
            Map.entry("btc-bech32-address", new Bech32AddressValidator()),
            Map.entry("eth-address", new Eip55AddressValidator()),
            Map.entry("us-bank-routing", new UsBankRoutingValidator()));

    private PatternValidators() {}

    /**
     * @param name registered validator name, or {@code null}
     * @return the validator, or empty if {@code name} is {@code null} or unregistered
     */
    public static Optional<PatternValidator> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(REGISTRY.get(name));
    }
}
