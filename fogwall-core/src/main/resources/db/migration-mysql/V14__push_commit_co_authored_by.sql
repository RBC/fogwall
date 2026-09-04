-- MySQL/MariaDB variant of db/migration/V14__push_commit_co_authored_by.sql — drops IF NOT EXISTS (unreliable across
-- MySQL/MariaDB versions; not needed since this migration only ever runs once, tracked in schema_migrations).
ALTER TABLE push_commits ADD COLUMN co_authored_by TEXT;
