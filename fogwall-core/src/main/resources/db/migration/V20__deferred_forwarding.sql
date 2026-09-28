-- Deferred forwarding: a server-mode push acknowledged to the client and forwarded upstream after approval, by
-- whichever instance records the approval. Runs on every engine; the pack chunk table differs only in its binary type
-- and is created by V20.1.

-- Whether the push was parked rather than held open for approval.
ALTER TABLE push_records ADD COLUMN deferred BOOLEAN NOT NULL DEFAULT FALSE;

-- The forwarding claim: the instance forwarding the push, and when that claim lapses if the instance dies holding it.
-- Taken and released with conditional updates on this row, so exactly one instance forwards a push.
ALTER TABLE push_records ADD COLUMN forward_claimed_by VARCHAR(255);
ALTER TABLE push_records ADD COLUMN forward_claim_expires_at TIMESTAMP NULL;

-- What a parked push needs for any instance to forward it. No foreign key to push_records: the pack is stored before
-- the record becomes reviewable, and rows left behind by a push whose record was never written are reclaimed by age.
CREATE TABLE IF NOT EXISTS parked_pushes (
    push_id        VARCHAR(36)   NOT NULL,
    provider_name  VARCHAR(100)  NOT NULL,
    forward_user   VARCHAR(255)  NOT NULL,
    upstream_url   VARCHAR(1024) NOT NULL,
    pack_bytes     BIGINT        NOT NULL,
    pack_sha256    VARCHAR(64)   NOT NULL,
    parked_at      TIMESTAMP     NOT NULL,
    CONSTRAINT pk_parked_pushes PRIMARY KEY (push_id)
);

-- The ref updates the client pushed, in the order it sent them.
CREATE TABLE IF NOT EXISTS parked_push_refs (
    push_id      VARCHAR(36)  NOT NULL,
    ordinal      INT          NOT NULL,
    ref_name     VARCHAR(512) NOT NULL,
    old_id       VARCHAR(40)  NOT NULL,
    new_id       VARCHAR(40)  NOT NULL,
    update_type  VARCHAR(30)  NOT NULL,
    CONSTRAINT pk_parked_push_refs PRIMARY KEY (push_id, ordinal)
);
