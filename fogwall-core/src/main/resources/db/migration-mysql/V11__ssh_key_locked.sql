-- MySQL/MariaDB variant of db/migration/V11__ssh_key_locked.sql — drops IF NOT EXISTS (unreliable across
-- MySQL/MariaDB versions for ADD COLUMN; not needed since this migration only ever runs once, tracked in
-- schema_migrations).
ALTER TABLE user_ssh_keys ADD COLUMN locked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_ssh_keys ADD COLUMN auth_source VARCHAR(20) NOT NULL DEFAULT 'config';
