-- The stored pack of a parked push, split into fixed-size chunks read back in seq order. Chunked rather than one value
-- so no single row approaches a driver or server packet limit.
CREATE TABLE IF NOT EXISTS parked_push_chunks (
    push_id  VARCHAR(36) NOT NULL,
    seq      INT         NOT NULL,
    data     BYTEA       NOT NULL,
    CONSTRAINT pk_parked_push_chunks PRIMARY KEY (push_id, seq)
);
