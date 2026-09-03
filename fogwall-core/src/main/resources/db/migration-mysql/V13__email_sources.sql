-- MySQL/MariaDB variant of db/migration/V13__email_sources.sql — drops IF NOT EXISTS (unreliable across
-- MySQL/MariaDB versions; not needed since this migration only ever runs once, tracked in schema_migrations).
CREATE TABLE email_sources (
    username    VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    auth_source VARCHAR(20)  NOT NULL,
    PRIMARY KEY (username, email, auth_source),
    FOREIGN KEY (username, email) REFERENCES user_emails(username, email) ON DELETE CASCADE
);

INSERT INTO email_sources (username, email, auth_source)
SELECT username, email, auth_source FROM user_emails WHERE locked = TRUE AND auth_source <> 'local';
