package com.rbc.fogwall.service;

import com.rbc.fogwall.user.GitCredential;
import com.rbc.fogwall.user.GitCredentialNameConflictException;
import com.rbc.fogwall.user.GitCredentialStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Issues, rotates, revokes and verifies the git credentials fogwall gives its users for server-mode HTTP pushes.
 *
 * <p>A credential reads {@code fgw_<id>_<secret>}. The id is a non-secret lookup key, so verifying a credential costs
 * one indexed read and one hash comparison rather than a comparison against every credential on file. Only the secret's
 * SHA-256 hash is stored: fogwall can verify a credential but never recover it, so it is shown to its owner once, when
 * issued. The secret is 256 random bits, so a fast hash leaves nothing to guess, and verifying costs next to nothing on
 * every authenticated request. A slow password hash only adds value for secrets people choose.
 *
 * <p>A credential identifies a fogwall user to fogwall and nothing else. It gives no access on any SCM provider.
 */
@Slf4j
public class GitCredentialService {

    /** Every fogwall-issued credential starts with this, so one can be recognised without a lookup. */
    public static final String PREFIX = "fgw_";

    static final int ID_LENGTH = 16;
    private static final int SECRET_BYTES = 32;
    static final int MAX_NAME_LENGTH = 100;
    private static final String ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz234567";

    /** Prefixes a stored hash with its algorithm, so the encoding can change without guessing at old rows. */
    static final String HASH_PREFIX = "{sha256}";

    /** How stale a credential's last-used time may be before a use is written back. Bounds writes on busy paths. */
    static final Duration USE_RECORD_INTERVAL = Duration.ofMinutes(5);

    /** A credential just issued or rotated, with the one copy of its value fogwall will ever hand out. */
    public record Issued(GitCredential credential, String value) {}

    private final GitCredentialStore store;
    private final Duration maxLifetime;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** @param maxLifetime how long a credential works after it is issued or rotated; empty for no limit */
    public GitCredentialService(GitCredentialStore store, Optional<Duration> maxLifetime) {
        this(store, maxLifetime, Clock.systemUTC());
    }

    GitCredentialService(GitCredentialStore store, Optional<Duration> maxLifetime, Clock clock) {
        this.store = Objects.requireNonNull(store, "store is required: it holds the credentials this service verifies");
        this.maxLifetime = maxLifetime.orElse(null);
        this.clock = clock;
    }

    /** Whether {@code value} has the shape of a fogwall-issued credential. Says nothing about whether it is valid. */
    public static boolean isFogwallCredential(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * Issues a new credential to {@code username}.
     *
     * @throws IllegalArgumentException if the name is blank, too long or contains control characters
     * @throws GitCredentialNameConflictException if the user already has one by this name
     */
    public Issued issue(String username, String name) {
        String validName = validateName(name);
        String id = randomId();
        String secret = randomSecret();
        Instant now = clock.instant();
        var credential = new GitCredential(id, username, validName, hash(secret), now, expiryFrom(now), null);
        store.save(credential);
        log.info("Issued git credential '{}' ({}) to user '{}'", validName, id, username);
        return new Issued(credential, format(id, secret));
    }

    /**
     * Replaces the secret of one of {@code username}'s credentials, keeping its id and name. The old value stops
     * working at once. Empty when the user holds no credential with this id.
     */
    public Optional<Issued> rotate(String username, String id) {
        Optional<GitCredential> existing =
                store.findById(id).filter(c -> c.username().equals(username));
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        String secret = randomSecret();
        Instant now = clock.instant();
        String hash = hash(secret);
        if (!store.replaceSecret(id, hash, now, expiryFrom(now))) {
            return Optional.empty();
        }
        GitCredential old = existing.get();
        log.info("Rotated git credential '{}' ({}) of user '{}'", old.name(), id, username);
        var rotated = new GitCredential(id, username, old.name(), hash, now, expiryFrom(now), null);
        return Optional.of(new Issued(rotated, format(id, secret)));
    }

    /** Returns the credentials issued to {@code username}, ordered by name. */
    public List<GitCredential> list(String username) {
        return store.findByUsername(username);
    }

    /** Revokes one of {@code username}'s credentials. Returns false when the user holds no credential with this id. */
    public boolean revoke(String username, String id) {
        boolean owned =
                store.findById(id).filter(c -> c.username().equals(username)).isPresent();
        if (owned && store.delete(id)) {
            log.info("Revoked git credential {} of user '{}'", id, username);
            return true;
        }
        return false;
    }

    /**
     * Returns the credential {@code value} is, if it is a current fogwall-issued credential: well-formed, on file, not
     * expired, and its secret matches.
     */
    public Optional<GitCredential> authenticate(String value) {
        Optional<Parsed> parsed = parse(value);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        Optional<GitCredential> found = store.findById(parsed.get().id());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        GitCredential credential = found.get();
        Instant now = clock.instant();
        if (isExpired(credential, now)) {
            log.info("Git credential {} of user '{}' has expired", credential.id(), credential.username());
            return Optional.empty();
        }
        if (!matches(parsed.get().secret(), credential.secretHash())) {
            log.warn(
                    "Git credential {} of user '{}' presented with the wrong secret",
                    credential.id(),
                    credential.username());
            return Optional.empty();
        }
        if (credential.lastUsedAt() == null
                || credential.lastUsedAt().plus(USE_RECORD_INTERVAL).isBefore(now)) {
            store.recordUse(credential.id(), now);
        }
        return Optional.of(credential);
    }

    /**
     * When the credential stops working, or null if it never does. The configured lifetime is applied to when the
     * credential was issued, not only to the expiry stored with it, so lowering the limit also retires credentials
     * issued before it.
     */
    public Instant effectiveExpiry(GitCredential credential) {
        Instant stored = credential.expiresAt();
        Instant byLimit = maxLifetime != null ? credential.createdAt().plus(maxLifetime) : null;
        if (stored == null || byLimit == null) {
            return stored != null ? stored : byLimit;
        }
        return stored.isBefore(byLimit) ? stored : byLimit;
    }

    /** Whether the credential has stopped working. */
    public boolean isExpired(GitCredential credential) {
        return isExpired(credential, clock.instant());
    }

    private boolean isExpired(GitCredential credential, Instant now) {
        Instant expiry = effectiveExpiry(credential);
        return expiry != null && !now.isBefore(expiry);
    }

    private Instant expiryFrom(Instant issuedAt) {
        return maxLifetime != null ? issuedAt.plus(maxLifetime) : null;
    }

    private static String validateName(String name) {
        String trimmed = name != null ? name.strip() : "";
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("A credential needs a name, such as the machine it will be used from");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("A credential name is at most " + MAX_NAME_LENGTH + " characters");
        }
        if (trimmed.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("A credential name cannot contain control characters");
        }
        return trimmed;
    }

    private record Parsed(String id, String secret) {}

    private static Optional<Parsed> parse(String value) {
        if (!isFogwallCredential(value)) {
            return Optional.empty();
        }
        int idStart = PREFIX.length();
        int separator = idStart + ID_LENGTH;
        if (value.length() <= separator + 1 || value.charAt(separator) != '_') {
            return Optional.empty();
        }
        String id = value.substring(idStart, separator);
        if (!id.chars().allMatch(c -> ID_ALPHABET.indexOf(c) >= 0)) {
            return Optional.empty();
        }
        return Optional.of(new Parsed(id, value.substring(separator + 1)));
    }

    static String hash(String secret) {
        return HASH_PREFIX + HexFormat.of().formatHex(sha256(secret));
    }

    /** Compares in constant time, so the time taken says nothing about how much of a guess was right. */
    private static boolean matches(String secret, String storedHash) {
        byte[] expected = storedHash.getBytes(StandardCharsets.UTF_8);
        byte[] actual = hash(secret).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime provides SHA-256", e);
        }
    }

    private static String format(String id, String secret) {
        return PREFIX + id + "_" + secret;
    }

    private String randomId() {
        var id = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            id.append(ID_ALPHABET.charAt(random.nextInt(ID_ALPHABET.length())));
        }
        return id.toString();
    }

    private String randomSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
