-- Widen provider columns to accommodate type/host values where host can be up
-- to 253 characters (RFC 1035). MySQL/MariaDB variant of
-- db/migration-postgresql/V2_1__widen_provider_columns.sql — MySQL has no
-- ALTER COLUMN ... TYPE syntax, so the full column definition is restated via
-- MODIFY COLUMN instead. H2 (dev/test) uses VARCHAR(100) from V1 and does not
-- need widening (always a fresh schema).

ALTER TABLE push_records        MODIFY COLUMN provider VARCHAR(300);

-- user_scm_identities has a PRIMARY KEY spanning (username, provider, scm_username). At
-- username(255) + scm_username(255), a full-width 300-char provider would push the key past
-- InnoDB's 3072-byte key limit, so the PK is redeclared here with a 240-char prefix on provider.
-- The UNIQUE(provider, scm_username) constraint stays full-width, so true duplicates are still
-- always caught; a prefix collision on the PK alone would only cause a spurious duplicate-key
-- rejection on insert (fails safe, no silent data mixing), and only for two providers identical
-- in their first 240 characters — not realistic for type/host provider strings.
ALTER TABLE user_scm_identities
    DROP PRIMARY KEY,
    MODIFY COLUMN provider VARCHAR(300) NOT NULL,
    ADD PRIMARY KEY (username, provider(240), scm_username);

ALTER TABLE access_rules        MODIFY COLUMN provider VARCHAR(300);
ALTER TABLE fetch_records       MODIFY COLUMN provider VARCHAR(300);
ALTER TABLE scm_token_cache     MODIFY COLUMN provider VARCHAR(300) NOT NULL;
ALTER TABLE repo_permissions    MODIFY COLUMN provider VARCHAR(300) NOT NULL;
