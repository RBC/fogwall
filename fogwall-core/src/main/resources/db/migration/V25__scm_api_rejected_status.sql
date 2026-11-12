-- A refusal by policy is recorded as REJECTED, like a refusal for content and like a refused push; the reason column
-- keeps why. Rewrites the records written while it had its own DENIED status.
UPDATE scm_api_action_records SET status = 'REJECTED' WHERE status = 'DENIED';
