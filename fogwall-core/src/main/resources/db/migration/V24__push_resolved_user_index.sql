-- Pushes are filtered by the fogwall user they resolved to, never by the username the client sent, so the index
-- belongs on resolved_user.
CREATE INDEX idx_push_records_resolved_user ON push_records (resolved_user);
DROP INDEX IF EXISTS idx_push_records_user;
