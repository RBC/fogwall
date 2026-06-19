-- Unify access_rules and repo_permissions onto a common (target, match_value, match_type) shape.
-- Closes #221.
--
-- access_rules: replaces slug/owner/name columns with target + match_value + match_type.
--   Data migration: whichever of slug/owner/name is non-null becomes the new target+match_value.
--   Match type: values prefixed with 'regex:' become REGEX (prefix stripped); all others become GLOB
--   (the previous implicit behaviour — glob syntax was always the default for non-regex patterns).
--
-- repo_permissions: renames path → match_value, path_type → match_type; adds target column (default SLUG).

-- ---------------------------------------------------------------------------
-- access_rules
-- ---------------------------------------------------------------------------

ALTER TABLE access_rules ADD COLUMN target      VARCHAR(10);
ALTER TABLE access_rules ADD COLUMN match_value VARCHAR(512);
ALTER TABLE access_rules ADD COLUMN match_type  VARCHAR(10);

-- Migrate slug rows
UPDATE access_rules SET target = 'SLUG',  match_value = slug  WHERE slug  IS NOT NULL;
UPDATE access_rules SET target = 'OWNER', match_value = owner WHERE owner IS NOT NULL;
UPDATE access_rules SET target = 'NAME',  match_value = name  WHERE name  IS NOT NULL;

-- Rows with a 'regex:' prefix become REGEX; strip the prefix from match_value
UPDATE access_rules
    SET match_type = 'REGEX', match_value = SUBSTRING(match_value, 8)
    WHERE match_value LIKE 'regex:%';

-- Everything else was implicitly glob
UPDATE access_rules SET match_type = 'GLOB' WHERE match_type IS NULL;

-- Apply NOT NULL after data migration
ALTER TABLE access_rules ALTER COLUMN target      SET NOT NULL;
ALTER TABLE access_rules ALTER COLUMN match_value SET NOT NULL;
ALTER TABLE access_rules ALTER COLUMN match_type  SET NOT NULL;

ALTER TABLE access_rules DROP COLUMN slug;
ALTER TABLE access_rules DROP COLUMN owner;
ALTER TABLE access_rules DROP COLUMN name;

-- ---------------------------------------------------------------------------
-- repo_permissions
-- ---------------------------------------------------------------------------

ALTER TABLE repo_permissions ADD COLUMN target      VARCHAR(10)  NOT NULL DEFAULT 'SLUG';
ALTER TABLE repo_permissions ADD COLUMN match_value VARCHAR(512);
ALTER TABLE repo_permissions ADD COLUMN match_type  VARCHAR(10);

UPDATE repo_permissions SET match_value = path, match_type = path_type;

ALTER TABLE repo_permissions ALTER COLUMN match_value SET NOT NULL;
ALTER TABLE repo_permissions ALTER COLUMN match_type  SET NOT NULL;

ALTER TABLE repo_permissions DROP COLUMN path;
ALTER TABLE repo_permissions DROP COLUMN path_type;
