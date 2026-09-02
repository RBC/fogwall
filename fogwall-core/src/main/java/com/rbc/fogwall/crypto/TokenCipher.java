package com.rbc.fogwall.crypto;

/** Encrypts/decrypts credential material (e.g. OAuth tokens) for storage at rest. */
public interface TokenCipher {

    /** Encrypts {@code plaintext}, returning a self-contained blob suitable for storage. */
    byte[] encrypt(byte[] plaintext);

    /**
     * Decrypts a blob produced by {@link #encrypt(byte[])}.
     *
     * @throws TokenCipherException if the blob is malformed or fails authentication (tampered/corrupted, or encrypted
     *     under a different key)
     */
    byte[] decrypt(byte[] blob);
}
