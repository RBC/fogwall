-- Tracks every OAuth provider that has verified a given SSH key (#40) — a key can legitimately be registered on
-- more than one linked provider (the same keypair used for both GitHub and GitLab, say). Without this, unlinking
-- one provider could delete a key still verified by another still-linked provider. user_ssh_keys.auth_source
-- remains the first-recorded "primary" label shown in the UI; this table is the source of truth for whether a key
-- survives an unlink.
CREATE TABLE IF NOT EXISTS ssh_key_sources (
    ssh_key_id  VARCHAR(36) NOT NULL REFERENCES user_ssh_keys(id) ON DELETE CASCADE,
    auth_source VARCHAR(20) NOT NULL,
    PRIMARY KEY (ssh_key_id, auth_source)
);

-- Backfill: every currently OAuth-locked key (not config-locked) has exactly one known source today.
INSERT INTO ssh_key_sources (ssh_key_id, auth_source)
SELECT id, auth_source FROM user_ssh_keys WHERE locked = TRUE AND auth_source <> 'config';
