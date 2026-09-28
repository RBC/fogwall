-- Git credentials fogwall issues to its users for server-mode HTTP pushes. The secret is held only as a bcrypt hash, so
-- fogwall can verify a credential but never recover it. The id is the non-secret lookup key, carried in the credential
-- itself. Table-level constraints only, so this one file applies unchanged on every supported engine.
CREATE TABLE IF NOT EXISTS user_git_credentials (
    id            VARCHAR(32)  NOT NULL,
    username      VARCHAR(255) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    secret_hash   VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    expires_at    TIMESTAMP    NULL,
    last_used_at  TIMESTAMP    NULL,
    CONSTRAINT pk_user_git_credentials PRIMARY KEY (id),
    CONSTRAINT uq_user_git_credentials_name UNIQUE (username, name),
    CONSTRAINT fk_user_git_credentials_user FOREIGN KEY (username) REFERENCES proxy_users (username) ON DELETE CASCADE
);
