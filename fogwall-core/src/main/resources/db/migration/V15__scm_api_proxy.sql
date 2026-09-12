-- SCM API proxy: schema for the whole feature, grouped into one migration since none of it has shipped yet
-- (no production database has ever run these tables — feel free to keep grouping future pre-release changes here
-- rather than adding new versions, per project convention: migrations only need to be immutable once released).
--
-- Runs on every engine. The one MySQL/MariaDB difference — variables_json needs MEDIUMTEXT there, see that column —
-- is a separate MySQL-only follow-up (V15.1) rather than a copy of this file. Indexes are created without
-- IF NOT EXISTS, which MySQL lacks; a migration runs once per version, so the guard buys nothing anywhere.

-- Opaque GraphQL node ID -> owner/repo resolution cache. GraphQL mutations (GitHub) reference their target only by
-- an opaque node ID, never by owner/repo, so the SCM API proxy must resolve one before it can run the permission
-- check. The TTL (enforced on read via cached_at, same pattern as scm_token_cache/ssh_fingerprint_cache) is a
-- security parameter, not just a perf knob — see docs/internals/SCM_API_PROXY.md §3c: a node ID can outlive a repo
-- rename/transfer while the owner/repo it resolves to changes underneath it.
--
-- GitHub's own table: each dialect that resolves an opaque identifier keeps its own, rather than sharing one column
-- that would have to be named for whichever API got there first. GitLab's equivalent is
-- scm_api_gitlab_project_cache below.
CREATE TABLE IF NOT EXISTS scm_api_github_node_cache (
    provider    VARCHAR(100) NOT NULL,
    node_id     VARCHAR(255) NOT NULL,
    repo_owner  VARCHAR(255) NOT NULL,
    repo_name   VARCHAR(255) NOT NULL,
    cached_at   TIMESTAMP    NOT NULL,
    PRIMARY KEY (provider, node_id)
);

-- GitLab numeric project ID -> owner/repo resolution cache. Kept separate from scm_api_github_node_cache on purpose:
-- "node ID" is GitHub GraphQL vocabulary, and a GitLab project ID is a different identifier from a different API,
-- so sharing one column would make the schema describe something that does not exist. Same TTL semantics and the
-- same security reasoning: an ID outlives a rename or transfer while what it resolves to changes underneath it.
--
-- Needed because `glab mr create` addresses the SOURCE project in the URL and carries the upstream only as a numeric
-- target_project_id in the request body, so authorizing on the URL alone would check the fork rather than the
-- upstream the merge request is opened on. See docs/internals/SCM_API_PROXY.md.
CREATE TABLE IF NOT EXISTS scm_api_gitlab_project_cache (
    provider    VARCHAR(100) NOT NULL,
    project_id  VARCHAR(255) NOT NULL,
    repo_owner  VARCHAR(255) NOT NULL,
    repo_name   VARCHAR(255) NOT NULL,
    cached_at   TIMESTAMP    NOT NULL,
    PRIMARY KEY (provider, project_id)
);

-- Audit trail: one record per proxied mutation. Same auditability bar as push_records — who, which rule, what
-- target, what evidence. Write-once: a mutation's allowlist/permission decision and forward outcome are all known
-- synchronously, so unlike push_records there is no update path.
CREATE TABLE IF NOT EXISTS scm_api_action_records (
    id              VARCHAR(36)  PRIMARY KEY,
    timestamp       TIMESTAMP    NOT NULL,
    provider        VARCHAR(100) NOT NULL,
    scm_username    VARCHAR(255),
    resolved_user   VARCHAR(255),
    repo_owner      VARCHAR(255),
    repo_name       VARCHAR(255),
    -- Null when fogwall refused the request before it could name the operation: an endpoint matching no
    -- allowlist rule has no operation to record, and the refusal is still worth keeping. The reason column
    -- carries the method and path in that case.
    mutation_field  VARCHAR(100),
    node_id         VARCHAR(255),
    node_type       VARCHAR(30),
    status          VARCHAR(20)  NOT NULL,
    reason          TEXT,
    -- On MySQL/MariaDB this is widened to MEDIUMTEXT by V15.1: their TEXT caps at 64 KB and an SCM API entity body may
    -- run to the 4 MiB request bound.
    variables_json  TEXT,
    user_agent      VARCHAR(512),
    client_type     VARCHAR(32),
    -- Version parsed out of user_agent, best effort; null when the header carried none fogwall could
    -- read. Its own column because a CLI wire-format break is version-specific, and "which releases"
    -- is a query rather than a scan of the raw header, which is kept regardless.
    client_version  VARCHAR(64),
    -- What the upstream answered: its HTTP status, and the scm_api_entities row the mutation created or touched.
    upstream_status INT,
    entity_id     VARCHAR(36)
);

CREATE INDEX idx_scm_api_action_records_resolved_user ON scm_api_action_records (resolved_user);
CREATE INDEX idx_scm_api_action_records_timestamp ON scm_api_action_records (timestamp);

-- SCM API entity registry: the pull/merge requests and issues that exist upstream because the SCM API proxy forwarded the
-- mutation that created them, plus those a forwarded mutation later touched. Mutable current state ("PR 7 is
-- closed"), keyed on what the upstream calls the thing, as opposed to scm_api_action_records above, which is the
-- append-only decision log ("at 14:02 alice was allowed to close PR 7") and points here via entity_id.
--
-- entity_number rather than number: NUMBER is a data type in H2 (Oracle compatibility) and a reserved word
-- elsewhere. Issues and pull requests share one number space on GitHub and Forgejo but not on GitLab, so kind is
-- part of the key.
CREATE TABLE IF NOT EXISTS scm_api_entities (
    id                      VARCHAR(36)   PRIMARY KEY,
    provider                VARCHAR(100)  NOT NULL,
    repo_owner              VARCHAR(255)  NOT NULL,
    repo_name               VARCHAR(255)  NOT NULL,
    kind                    VARCHAR(20)   NOT NULL,
    entity_number         INT           NOT NULL,
    url                     VARCHAR(1024),
    -- GitHub's opaque GraphQL node ID; null on the REST dialects. A later GitHub mutation names its target by
    -- node ID alone, so this is how the row is found again.
    node_id                 VARCHAR(255),
    title                   VARCHAR(1024),
    state                   VARCHAR(20)   NOT NULL,
    created_by              VARCHAR(255),
    created_by_scm_username VARCHAR(255),
    created_at              TIMESTAMP     NOT NULL,
    updated_at              TIMESTAMP     NOT NULL,
    created_action_id       VARCHAR(36),
    last_action_id          VARCHAR(36),
    CONSTRAINT uq_scm_api_entities_target UNIQUE (provider, repo_owner, repo_name, kind, entity_number)
);

CREATE INDEX idx_scm_api_entities_node_id ON scm_api_entities (provider, node_id);
