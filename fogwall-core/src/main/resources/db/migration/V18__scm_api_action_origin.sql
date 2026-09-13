-- Which fogwall surface ran the action: DASHBOARD for the dashboard's own write path, SCM_API for a mutation the SCM
-- API proxy forwarded. Its own column rather than a reading of client_type, which classifies the caller's User-Agent
-- and is forgeable by construction — this is fogwall's account of its own entry point, so a view may filter on it.
ALTER TABLE scm_api_action_records ADD COLUMN origin VARCHAR(20);

-- Existing rows keep their place in the trail: before this column, the dashboard stamped the literal 'dashboard' into
-- client_type and every other value there is a User-Agent classification, which only the proxy path produces.
UPDATE scm_api_action_records
SET origin = CASE WHEN LOWER(client_type) = 'dashboard' THEN 'DASHBOARD' ELSE 'SCM_API' END
WHERE origin IS NULL;

-- The Activity view filters on surface, on its own and alongside "my actions": one index serves both.
CREATE INDEX idx_scm_api_action_records_origin_user ON scm_api_action_records (origin, resolved_user);
