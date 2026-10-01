-- MySQL/MariaDB variant of db/migration/V21__fetch_activity.sql. The timestamps carry an explicit default, so a
-- MariaDB without explicit_defaults_for_timestamp does not rewrite bucket_start on every update, and the repo index is
-- prefixed to stay inside InnoDB's 3072-byte key limit under utf8mb4.
CREATE TABLE fetch_activity (
    id           VARCHAR(64)  PRIMARY KEY,
    bucket_start TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    provider     VARCHAR(300),
    owner        VARCHAR(255),
    repo_name    VARCHAR(255),
    transport    VARCHAR(10)  NOT NULL,
    proxy_mode   VARCHAR(20)  NOT NULL,
    result       VARCHAR(10)  NOT NULL,
    refusal      VARCHAR(40),
    rule_id      VARCHAR(255),
    fetch_count  BIGINT       NOT NULL,
    last_seen    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_fetch_activity_bucket ON fetch_activity (bucket_start);
CREATE INDEX idx_fetch_activity_repo ON fetch_activity (provider(100), owner(150), repo_name(150), bucket_start);
