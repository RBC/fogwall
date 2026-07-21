package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Base58Check validation for legacy Bitcoin addresses (P2PKH/P2SH, starting "1"/"3"): decode base58, split off the
 * trailing 4-byte checksum, and confirm it equals the first 4 bytes of SHA-256(SHA-256(payload)). Native SegWit
 * (bech32, "bc1...") addresses use a different encoding and are not covered by this validator.
 */
public class BitcoinAddressValidator implements PatternValidator {

    private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final BigInteger BASE = BigInteger.valueOf(58);

    @Override
    public boolean isValid(String matchedText) {
        byte[] decoded = decodeBase58(matchedText.strip());
        if (decoded == null || decoded.length != 25) {
            return false;
        }
        byte[] payload = Arrays.copyOfRange(decoded, 0, 21);
        byte[] checksum = Arrays.copyOfRange(decoded, 21, 25);
        byte[] expected = Arrays.copyOfRange(sha256(sha256(payload)), 0, 4);
        return Arrays.equals(checksum, expected);
    }

    private static byte[] decodeBase58(String input) {
        BigInteger num = BigInteger.ZERO;
        int leadingZeros = 0;
        boolean stillLeading = true;
        for (char c : input.toCharArray()) {
            int digit = ALPHABET.indexOf(c);
            if (digit < 0) {
                return null;
            }
            if (stillLeading) {
                if (c == '1') {
                    leadingZeros++;
                } else {
                    stillLeading = false;
                }
            }
            num = num.multiply(BASE).add(BigInteger.valueOf(digit));
        }

        byte[] bytes = num.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        byte[] result = new byte[leadingZeros + bytes.length];
        System.arraycopy(bytes, 0, result, leadingZeros, bytes.length);
        return result;
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
