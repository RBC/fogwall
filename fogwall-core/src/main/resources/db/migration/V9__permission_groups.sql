-- Named permission groups. Members of a group inherit the group's repo permission rules.
CREATE TABLE IF NOT EXISTS permission_groups (
    id          VARCHAR(36)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(512),
    source      VARCHAR(10)  NOT NULL DEFAULT 'DB'   -- CONFIG | DB
);

-- Repository permission rules attached to a group (same shape as repo_permissions but group-scoped).
CREATE TABLE IF NOT EXISTS group_permissions (
    id          VARCHAR(36)  PRIMARY KEY,
    group_id    VARCHAR(36)  NOT NULL REFERENCES permission_groups(id) ON DELETE CASCADE,
    provider    VARCHAR(100) NOT NULL,
    target      VARCHAR(20)  NOT NULL DEFAULT 'SLUG',
    match_value VARCHAR(512) NOT NULL,
    match_type  VARCHAR(10)  NOT NULL DEFAULT 'GLOB',
    operation   VARCHAR(20)  NOT NULL DEFAULT 'PUSH'
);

-- Group membership: which users belong to which groups.
CREATE TABLE IF NOT EXISTS group_members (
    group_id    VARCHAR(36)  NOT NULL REFERENCES permission_groups(id) ON DELETE CASCADE,
    username    VARCHAR(255) NOT NULL REFERENCES proxy_users(username) ON DELETE CASCADE,
    PRIMARY KEY (group_id, username)
);

CREATE INDEX IF NOT EXISTS idx_group_permissions_group_id ON group_permissions(group_id);
CREATE INDEX IF NOT EXISTS idx_group_permissions_provider ON group_permissions(provider);
CREATE INDEX IF NOT EXISTS idx_group_members_username ON group_members(username);
