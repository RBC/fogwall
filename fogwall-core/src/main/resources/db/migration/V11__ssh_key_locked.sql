-- Marks SSH keys imported from a verified source (SCM OAuth import, #40) as not user-removable via the dashboard —
-- same trust tier as OAuth-verified emails (upsertLockedEmail), for keys proven rather than self-asserted.
ALTER TABLE user_ssh_keys ADD COLUMN IF NOT EXISTS locked BOOLEAN NOT NULL DEFAULT FALSE;

-- Tracks which identity source locked a key (#40) — mirrors user_emails.auth_source. Needed so unlinking one OAuth
-- provider only affects keys it imported, not keys locked by config or a different linked provider.
ALTER TABLE user_ssh_keys ADD COLUMN IF NOT EXISTS auth_source VARCHAR(20) NOT NULL DEFAULT 'config';
