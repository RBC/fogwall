-- OAuth tokens obtained by linking a proxy user's account to an upstream SCM provider (#40) —
-- MySQL/MariaDB variant of db/migration/V10__scm_oauth_tokens.sql: BLOB instead of BYTEA (MySQL/MariaDB
-- have no BYTEA type; see V4's mysql variant for the same substitution).
CREATE TABLE IF NOT EXISTS user_scm_tokens (
    username        VARCHAR(255) NOT NULL REFERENCES proxy_users(username) ON DELETE CASCADE,
    provider        VARCHAR(100) NOT NULL,
    access_token    BLOB         NOT NULL,
    refresh_token   BLOB,
    scopes          VARCHAR(512),
    expires_at      TIMESTAMP,
    authorized_at   TIMESTAMP    NOT NULL,
    PRIMARY KEY (username, provider)
);
