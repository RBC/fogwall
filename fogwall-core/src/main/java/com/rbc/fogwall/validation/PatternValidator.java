package com.rbc.fogwall.validation;

/**
 * Structural validation applied to text already matched by a {@link PatternRule}'s regex - e.g. a Luhn checksum, or
 * rejection of known placeholder values. Named and looked up via {@link PatternValidators}.
 */
@FunctionalInterface
public interface PatternValidator {

    /**
     * @param matchedText the substring matched by the rule's regex
     * @return true if the matched text passes structural validation
     */
    boolean isValid(String matchedText);
}
