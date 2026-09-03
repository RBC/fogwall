-- Same multi-source problem as ssh_key_sources (#40), for emails: the same address can be reported as verified by
-- more than one linked provider (e.g. a personal email registered on both GitHub and GitLab). user_emails.auth_source
-- remains the first-recorded "primary" label; this table lets the UI show every source and lets unlinking one
-- provider leave an email another still verifies untouched.
CREATE TABLE IF NOT EXISTS email_sources (
    username    VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    auth_source VARCHAR(20)  NOT NULL,
    PRIMARY KEY (username, email, auth_source),
    FOREIGN KEY (username, email) REFERENCES user_emails(username, email) ON DELETE CASCADE
);

INSERT INTO email_sources (username, email, auth_source)
SELECT username, email, auth_source FROM user_emails WHERE locked = TRUE AND auth_source <> 'local';
