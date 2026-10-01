-- Hourly counts of fogwall's decisions on clones and fetches, one row per combination of what was fetched, how, and
-- with what result. fetch_records is no longer written; it is left in place for operators to drop.
CREATE TABLE fetch_activity (
    -- Hash of bucket_start and every dimension below. Several dimensions may be null, which a composite unique key
    -- would treat as distinct, so the flush finds its row by this instead.
    id           VARCHAR(64)  PRIMARY KEY,
    bucket_start TIMESTAMP    NOT NULL,     -- start of the hour, UTC
    provider     VARCHAR(300),
    owner        VARCHAR(255),              -- null on the row that collects keys past the in-memory cap
    repo_name    VARCHAR(255),              -- null on the row that collects keys past the in-memory cap
    transport    VARCHAR(10)  NOT NULL,     -- HTTP or SSH
    proxy_mode   VARCHAR(20)  NOT NULL,     -- SERVER or TRANSPARENT
    result       VARCHAR(10)  NOT NULL,     -- ALLOWED or BLOCKED
    refusal      VARCHAR(40),               -- why a BLOCKED fetch was refused
    rule_id      VARCHAR(255),              -- the URL rule that matched, if one did
    fetch_count  BIGINT       NOT NULL,
    last_seen    TIMESTAMP    NOT NULL
);

CREATE INDEX idx_fetch_activity_bucket ON fetch_activity (bucket_start);
CREATE INDEX idx_fetch_activity_repo ON fetch_activity (provider, owner, repo_name, bucket_start);
