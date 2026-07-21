package com.rbc.fogwall.validation.checksum;

import com.rbc.fogwall.validation.PatternValidator;
import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * ISO 7064 mod-97-10 checksum for IBANs: rearrange (move the first 4 characters to the end), convert letters to their
 * base-36 ordinal (A=10..Z=35), and check the resulting number mod 97 == 1.
 */
public class IbanValidator implements PatternValidator {

    private static final Pattern SHAPE = Pattern.compile("^[A-Z]{2}\\d{2}[A-Z0-9]{11,30}$");
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    @Override
    public boolean isValid(String matchedText) {
        String iban = matchedText.replaceAll("\\s", "").toUpperCase();
        if (!SHAPE.matcher(iban).matches()) {
            return false;
        }

        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder numeric = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            if (Character.isDigit(c)) {
                numeric.append(c);
            } else {
                numeric.append(c - 'A' + 10);
            }
        }
        return new BigInteger(numeric.toString()).mod(NINETY_SEVEN).equals(BigInteger.ONE);
    }
}
