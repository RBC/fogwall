package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.bouncycastle.jcajce.provider.digest.Keccak;

/**
 * EIP-55 mixed-case checksum for Ethereum addresses: each hex digit of the (lowercased) address is uppercased if the
 * corresponding nibble of Keccak-256(lowercased address) is &gt;= 8. Only checksum-cased addresses can be verified this
 * way - an all-lowercase or all-uppercase address carries no checksum signal and is rejected here rather than treated
 * as a false negative, since there's nothing to validate against.
 */
public class Eip55AddressValidator implements PatternValidator {

    private static final Pattern SHAPE = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    @Override
    public boolean isValid(String matchedText) {
        String address = matchedText.strip();
        if (!SHAPE.matcher(address).matches()) {
            return false;
        }
        String hex = address.substring(2);
        String lower = hex.toLowerCase();
        if (lower.equals(hex) || hex.toUpperCase().equals(hex)) {
            return false;
        }

        byte[] hash = new Keccak.Digest256().digest(lower.getBytes(StandardCharsets.US_ASCII));
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            int nibble = (hash[i / 2] >> (i % 2 == 0 ? 4 : 0)) & 0xf;
            char c = lower.charAt(i);
            expected.append(Character.isDigit(c) || nibble < 8 ? c : Character.toUpperCase(c));
        }
        return expected.toString().equals(hex);
    }
}
