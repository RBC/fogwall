-- Pushes are filtered by the fogwall user they resolved to, never by the username the client sent, so the index
-- belongs on resolved_user. MySQL has no DROP INDEX IF EXISTS; V1 always created the old index.
CREATE INDEX idx_push_records_resolved_user ON push_records (resolved_user);
DROP INDEX idx_push_records_user ON push_records;
