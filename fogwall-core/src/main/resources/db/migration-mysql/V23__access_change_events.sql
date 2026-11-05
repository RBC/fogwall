-- MySQL/MariaDB variant of db/migration/V23__access_change_events.sql. occurred_at keeps milliseconds, which a bare
-- TIMESTAMP truncates to the second, and carries an explicit default so a MariaDB without
-- explicit_defaults_for_timestamp does not treat it as auto-updating.
CREATE TABLE permission_change_events (
    id              VARCHAR(36)  PRIMARY KEY,
    occurred_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    action          VARCHAR(10)  NOT NULL,
    origin          VARCHAR(20)  NOT NULL,
    actor           VARCHAR(255),
    source_revision VARCHAR(255),
    subject_type    VARCHAR(10)  NOT NULL,
    subject         VARCHAR(255) NOT NULL,
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
    occurred_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    action          VARCHAR(10)  NOT NULL,
    origin          VARCHAR(20)  NOT NULL,
    actor           VARCHAR(255),
    source_revision VARCHAR(255),
    group_id        VARCHAR(36)  NOT NULL,
    group_name      VARCHAR(255) NOT NULL,
    username        VARCHAR(255) NOT NULL
);

CREATE INDEX idx_membership_change_events_occurred ON membership_change_events (occurred_at, id);
CREATE INDEX idx_membership_change_events_username ON membership_change_events (username, occurred_at);
CREATE INDEX idx_membership_change_events_group ON membership_change_events (group_name, occurred_at);

CREATE TABLE role_change_events (
    id              VARCHAR(36)  PRIMARY KEY,
    occurred_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    origin          VARCHAR(20)  NOT NULL,
    actor           VARCHAR(255) NOT NULL,
    username        VARCHAR(255) NOT NULL,
    previous_roles  VARCHAR(255) NOT NULL,
    roles           VARCHAR(255) NOT NULL
);

CREATE INDEX idx_role_change_events_occurred ON role_change_events (occurred_at, id);
CREATE INDEX idx_role_change_events_username ON role_change_events (username, occurred_at);

-- granted_at is nullable (a grant that predates this has none), so it is declared NULL explicitly: MariaDB without
-- explicit_defaults_for_timestamp makes a bare TIMESTAMP column NOT NULL and auto-updating.
ALTER TABLE repo_permissions ADD COLUMN granted_by VARCHAR(255);
ALTER TABLE repo_permissions ADD COLUMN granted_at TIMESTAMP(3) NULL;
ALTER TABLE group_permissions ADD COLUMN granted_by VARCHAR(255);
ALTER TABLE group_permissions ADD COLUMN granted_at TIMESTAMP(3) NULL;
