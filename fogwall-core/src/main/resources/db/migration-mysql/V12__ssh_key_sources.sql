-- MySQL/MariaDB variant of db/migration/V12__ssh_key_sources.sql — drops IF NOT EXISTS (unreliable across
-- MySQL/MariaDB versions; not needed since this migration only ever runs once, tracked in schema_migrations).
CREATE TABLE ssh_key_sources (
    ssh_key_id  VARCHAR(36) NOT NULL REFERENCES user_ssh_keys(id) ON DELETE CASCADE,
    auth_source VARCHAR(20) NOT NULL,
    PRIMARY KEY (ssh_key_id, auth_source)
);

INSERT INTO ssh_key_sources (ssh_key_id, auth_source)
SELECT id, auth_source FROM user_ssh_keys WHERE locked = TRUE AND auth_source <> 'config';
