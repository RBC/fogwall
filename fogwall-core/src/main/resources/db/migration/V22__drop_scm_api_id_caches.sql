-- The SCM API proxy resolves GitHub node IDs and GitLab project IDs on every request: each survives a repository
-- rename or transfer while the owner/repo it names changes, so a cached mapping could authorize against the wrong
-- repository. The caches held only derived data.
DROP TABLE IF EXISTS scm_api_github_node_cache;
DROP TABLE IF EXISTS scm_api_gitlab_project_cache;
