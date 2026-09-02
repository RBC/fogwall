-- OAuth tokens obtained by linking a proxy user's account to an upstream SCM provider (#40).
-- Encrypted at rest by the application (AES-256-GCM: 12-byte IV || ciphertext || tag stored as one
-- blob) — this migration only defines storage shape, not the encryption itself.
CREATE TABLE IF NOT EXISTS user_scm_tokens (
    username        VARCHAR(255) NOT NULL REFERENCES proxy_users(username) ON DELETE CASCADE,
    provider        VARCHAR(100) NOT NULL,
    access_token    BYTEA        NOT NULL,
    refresh_token   BYTEA,
    scopes          VARCHAR(512),
    expires_at      TIMESTAMP,
    authorized_at   TIMESTAMP    NOT NULL,
    PRIMARY KEY (username, provider)
);
