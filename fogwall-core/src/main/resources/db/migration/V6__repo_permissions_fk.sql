-- Add FK from repo_permissions.username → proxy_users.username with cascade delete.
-- Orphaned rows (left behind by prior user deletions) are cleaned up first.

DELETE FROM repo_permissions
WHERE username NOT IN (SELECT username FROM proxy_users);

ALTER TABLE repo_permissions
ADD CONSTRAINT fk_repo_permissions_username
FOREIGN KEY (username) REFERENCES proxy_users(username) ON DELETE CASCADE;
