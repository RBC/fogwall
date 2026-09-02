package com.rbc.fogwall.crypto;

/** Thrown when a {@link TokenCipher} cannot encrypt/decrypt — malformed input, wrong key, or tampered ciphertext. */
public class TokenCipherException extends RuntimeException {

    public TokenCipherException(String message) {
        super(message);
    }

    public TokenCipherException(String message, Throwable cause) {
        super(message, cause);
    }
}
