-- Identical to db/migration/V15__scm_api_proxy.sql except standalone CREATE INDEX statements drop IF NOT EXISTS
-- (unsupported by MySQL; not needed there since this migration only ever runs once per version).
CREATE TABLE IF NOT EXISTS scm_api_github_node_cache (
    provider    VARCHAR(100) NOT NULL,
    node_id     VARCHAR(255) NOT NULL,
    repo_owner  VARCHAR(255) NOT NULL,
    repo_name   VARCHAR(255) NOT NULL,
    cached_at   TIMESTAMP    NOT NULL,
    PRIMARY KEY (provider, node_id)
);

CREATE TABLE IF NOT EXISTS scm_api_gitlab_project_cache (
    provider    VARCHAR(100) NOT NULL,
    project_id  VARCHAR(255) NOT NULL,
    repo_owner  VARCHAR(255) NOT NULL,
    repo_name   VARCHAR(255) NOT NULL,
    cached_at   TIMESTAMP    NOT NULL,
    PRIMARY KEY (provider, project_id)
);

CREATE TABLE IF NOT EXISTS scm_api_action_records (
    id              VARCHAR(36)  PRIMARY KEY,
    timestamp       TIMESTAMP    NOT NULL,
    provider        VARCHAR(100) NOT NULL,
    push_user       VARCHAR(255),
    resolved_user   VARCHAR(255),
    repo_owner      VARCHAR(255),
    repo_name       VARCHAR(255),
    mutation_field  VARCHAR(100) NOT NULL,
    node_id         VARCHAR(255),
    node_type       VARCHAR(30),
    status          VARCHAR(20)  NOT NULL,
    reason          TEXT,
    variables_json  TEXT,
    user_agent      VARCHAR(512),
    client_type     VARCHAR(32)
);

CREATE INDEX idx_scm_api_action_records_resolved_user ON scm_api_action_records (resolved_user);
CREATE INDEX idx_scm_api_action_records_timestamp ON scm_api_action_records (timestamp);

CREATE TABLE IF NOT EXISTS scm_api_access_rules (
    id          VARCHAR(36)  PRIMARY KEY,
    provider    VARCHAR(100) NOT NULL,
    operation   VARCHAR(10)  NOT NULL DEFAULT 'BOTH',
    access      VARCHAR(10)  NOT NULL DEFAULT 'ALLOW',
    source      VARCHAR(10)  NOT NULL DEFAULT 'DB'
);
