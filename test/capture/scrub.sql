-- Run against the captured H2 file BEFORE dumping (capture.py does this via :fogwall-dashboard:runH2Script).
-- Removes everything that is secret, session-bound, or a cache of live provider state. The tables stay (the
-- schema must match what the migrator expects); only their rows go.
--
-- Verified-identity badges do NOT depend on any of these: they read user_scm_identities.verified,
-- user_emails / email_sources and user_ssh_keys / ssh_key_sources, all of which are kept.

DELETE FROM user_scm_tokens;          -- encrypted OAuth access tokens (V10)
DELETE FROM scm_token_cache;          -- PAT → identity cache from real pushes
DELETE FROM ssh_fingerprint_cache;    -- upstream SSH key fingerprints fetched from providers
DELETE FROM spring_session_attributes;
DELETE FROM spring_session;
