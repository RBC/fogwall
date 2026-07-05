-- SSH public keys associated with proxy users. Each row represents one key a user has registered
-- via their profile page. The fingerprint is the SHA-256 hash in OpenSSH format (e.g.
-- "SHA256:...") and is used as the lookup key during SSH authentication.
CREATE TABLE IF NOT EXISTS user_ssh_keys (
    id          VARCHAR(36)  PRIMARY KEY,
    username    VARCHAR(255) NOT NULL REFERENCES proxy_users(username) ON DELETE CASCADE,
    fingerprint VARCHAR(255) NOT NULL UNIQUE,
    public_key  TEXT         NOT NULL,
    label       VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_ssh_keys_username ON user_ssh_keys(username);
