package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;

/**
 * BIP-173/BIP-350 checksum for native SegWit ("bc1q...") and Taproot ("bc1p...") Bitcoin addresses. Bech32 uses a
 * BCH-based polymod checksum entirely separate from Base58Check's SHA-256 checksum - see
 * {@link BitcoinAddressValidator} for legacy addresses. The witness version (first data character) selects which of the
 * two checksum constants (BIP-173 bech32 for v0, BIP-350 bech32m for v1+) applies.
 */
public class Bech32AddressValidator implements PatternValidator {

    private static final String CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";
    private static final int BECH32_CONST = 1;
    private static final int BECH32M_CONST = 0x2bc830a3;

    @Override
    public boolean isValid(String matchedText) {
        String address = matchedText.strip().toLowerCase();
        int separator = address.lastIndexOf('1');
        if (separator < 1 || separator + 7 > address.length()) {
            return false;
        }
        String hrp = address.substring(0, separator);
        String dataPart = address.substring(separator + 1);

        int[] data = new int[dataPart.length()];
        for (int i = 0; i < dataPart.length(); i++) {
            int value = CHARSET.indexOf(dataPart.charAt(i));
            if (value < 0) {
                return false;
            }
            data[i] = value;
        }

        int polymod = polymod(concat(expandHrp(hrp), data));
        if (polymod != BECH32_CONST && polymod != BECH32M_CONST) {
            return false;
        }

        int witnessVersion = data[0];
        int expectedConst = witnessVersion == 0 ? BECH32_CONST : BECH32M_CONST;
        return polymod == expectedConst;
    }

    private static int[] expandHrp(String hrp) {
        int[] expanded = new int[hrp.length() * 2 + 1];
        for (int i = 0; i < hrp.length(); i++) {
            expanded[i] = hrp.charAt(i) >> 5;
            expanded[i + hrp.length() + 1] = hrp.charAt(i) & 0x1f;
        }
        expanded[hrp.length()] = 0;
        return expanded;
    }

    private static int[] concat(int[] a, int[] b) {
        int[] result = new int[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static int polymod(int[] values) {
        int[] generator = {0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3};
        int chk = 1;
        for (int value : values) {
            int top = chk >>> 25;
            chk = ((chk & 0x1ffffff) << 5) ^ value;
            for (int i = 0; i < 5; i++) {
                if (((top >>> i) & 1) != 0) {
                    chk ^= generator[i];
                }
            }
        }
        return chk;
    }
}
