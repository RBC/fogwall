-- MySQL/MariaDB variant of db/migration/V20_1__parked_push_chunks.sql: MEDIUMBLOB instead of BYTEA, which these
-- engines lack. Their BLOB holds 64 KB, less than one chunk.
CREATE TABLE IF NOT EXISTS parked_push_chunks (
    push_id  VARCHAR(36) NOT NULL,
    seq      INT         NOT NULL,
    data     MEDIUMBLOB  NOT NULL,
    CONSTRAINT pk_parked_push_chunks PRIMARY KEY (push_id, seq)
);
