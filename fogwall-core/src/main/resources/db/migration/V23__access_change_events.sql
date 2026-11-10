-- Append-only records of changes to who may do what: grants and revocations of user and group repository permissions,
-- additions to and removals from permission groups, and changes to a user's roles. No foreign keys: an event outlives
-- the grant, user and group it names.
CREATE TABLE permission_change_events (
    id              VARCHAR(36)  PRIMARY KEY,
    occurred_at     TIMESTAMP    NOT NULL,
    action          VARCHAR(10)  NOT NULL,     -- GRANTED or REVOKED
    origin          VARCHAR(20)  NOT NULL,     -- DASHBOARD, API or CONFIG
    actor           VARCHAR(255),              -- who made the change; null for CONFIG
    source_revision VARCHAR(255),              -- configuration revision a CONFIG change was reloaded from
    subject_type    VARCHAR(10)  NOT NULL,     -- USER or GROUP
    subject         VARCHAR(255) NOT NULL,     -- username or group name
    permission_id   VARCHAR(36)  NOT NULL,
    provider        VARCHAR(300) NOT NULL,
    target          VARCHAR(20)  NOT NULL,
    match_value     VARCHAR(512) NOT NULL,
    match_type      VARCHAR(10)  NOT NULL,
    operation       VARCHAR(20)  NOT NULL
);

CREATE INDEX idx_permission_change_events_occurred ON permission_change_events (occurred_at, id);
CREATE INDEX idx_permission_change_events_subject ON permission_change_events (subject, occurred_at);
CREATE INDEX idx_permission_change_events_actor ON permission_change_events (actor, occurred_at);

CREATE TABLE membership_change_events (
    id              VARCHAR(36)  PRIMARY KEY,
    occurred_at     TIMESTAMP    NOT NULL,
    action          VARCHAR(10)  NOT NULL,     -- ADDED or REMOVED
    origin          VARCHAR(20)  NOT NULL,     -- DASHBOARD, API or CONFIG
    actor           VARCHAR(255),              -- who made the change; null for CONFIG
    source_revision VARCHAR(255),              -- configuration revision a CONFIG change was reloaded from
    group_id        VARCHAR(36)  NOT NULL,
    group_name      VARCHAR(255) NOT NULL,     -- the group's name when the change was made
    username        VARCHAR(255) NOT NULL      -- the member added or removed
);

CREATE INDEX idx_membership_change_events_occurred ON membership_change_events (occurred_at, id);
CREATE INDEX idx_membership_change_events_username ON membership_change_events (username, occurred_at);
CREATE INDEX idx_membership_change_events_group ON membership_change_events (group_name, occurred_at);

CREATE TABLE role_change_events (
    id              VARCHAR(36)  PRIMARY KEY,
    occurred_at     TIMESTAMP    NOT NULL,
    origin          VARCHAR(20)  NOT NULL,     -- DASHBOARD, API or IDP
    actor           VARCHAR(255) NOT NULL,     -- who made the change; for IDP, the identity provider
    username        VARCHAR(255) NOT NULL,     -- whose roles changed
    previous_roles  VARCHAR(255) NOT NULL,     -- comma-separated, sorted; empty for a new user
    roles           VARCHAR(255) NOT NULL      -- comma-separated, sorted; empty for a deleted user
);

CREATE INDEX idx_role_change_events_occurred ON role_change_events (occurred_at, id);
CREATE INDEX idx_role_change_events_username ON role_change_events (username, occurred_at);

-- Who granted each current grant, and when. Null on a grant that predates this, and granted_by is null on one loaded
-- from configuration.
ALTER TABLE repo_permissions ADD COLUMN granted_by VARCHAR(255);
ALTER TABLE repo_permissions ADD COLUMN granted_at TIMESTAMP;
ALTER TABLE group_permissions ADD COLUMN granted_by VARCHAR(255);
ALTER TABLE group_permissions ADD COLUMN granted_at TIMESTAMP;
