-- MySQL/MariaDB follow-up to the common V15: their TEXT holds 64 KB, and a proposal's request payload may run to the
-- 4 MiB request bound, so the column is widened here rather than by keeping a whole MySQL copy of V15.
ALTER TABLE scm_api_action_records MODIFY variables_json MEDIUMTEXT;
