-- Spring Session JDBC store tables — MySQL/MariaDB variant of db/migration/V4__spring_session.sql.
-- Mirrors Spring Session's own schema-mysql.sql: BLOB instead of BYTEA (MySQL/MariaDB have no BYTEA
-- type), and CREATE INDEX without IF NOT EXISTS (unsupported by MySQL as a standalone statement;
-- not needed since this migration only ever runs once, tracked in schema_migrations).
-- Created unconditionally on all JDBC backends so the schema is consistent regardless of whether
-- server.session-store=jdbc is currently configured. Tables are unused when another store is selected.

CREATE TABLE IF NOT EXISTS SPRING_SESSION (
    PRIMARY_ID            CHAR(36)     NOT NULL,
    SESSION_ID             CHAR(36)     NOT NULL,
    CREATION_TIME          BIGINT       NOT NULL,
    LAST_ACCESS_TIME       BIGINT       NOT NULL,
    MAX_INACTIVE_INTERVAL  INT          NOT NULL,
    EXPIRY_TIME            BIGINT       NOT NULL,
    PRINCIPAL_NAME         VARCHAR(100) DEFAULT NULL,
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BLOB         NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;
