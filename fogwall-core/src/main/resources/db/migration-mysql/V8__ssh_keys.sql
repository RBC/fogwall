-- SSH public keys associated with proxy users — MySQL/MariaDB variant of db/migration/V8__ssh_keys.sql.
-- Identical except standalone CREATE INDEX statements drop IF NOT EXISTS (unsupported by MySQL; not
-- needed since this migration only ever runs once, tracked in schema_migrations).
CREATE TABLE IF NOT EXISTS user_ssh_keys (
    id          VARCHAR(36)  PRIMARY KEY,
    username    VARCHAR(255) NOT NULL REFERENCES proxy_users(username) ON DELETE CASCADE,
    fingerprint VARCHAR(255) NOT NULL UNIQUE,
    public_key  TEXT         NOT NULL,
    label       VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_user_ssh_keys_username ON user_ssh_keys(username);

-- Cache of SSH public key fingerprints fetched from upstream SCM providers.
-- Keyed by (provider, scm_login); the fingerprints column stores a comma-separated
-- list of SHA-256 fingerprint strings. Entries are re-fetched after cached_at exceeds
-- the configured TTL (default 7 days).
CREATE TABLE IF NOT EXISTS ssh_fingerprint_cache (
    provider    VARCHAR(100) NOT NULL,
    scm_login   VARCHAR(255) NOT NULL,
    fingerprints TEXT        NOT NULL,
    cached_at   TIMESTAMP    NOT NULL,
    PRIMARY KEY (provider, scm_login)
);
