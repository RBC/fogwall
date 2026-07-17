package com.rbc.fogwall.config;

import com.rbc.fogwall.db.jdbc.PoolConfig;
import lombok.Data;

/** Binds the {@code database:} block in fogwall.yml. */
@Data
public class DatabaseConfig {

    /**
     * Storage backend. Values: {@code h2-mem} (default), {@code h2-file}, {@code postgres}, {@code mysql},
     * {@code mariadb}, {@code mongo}.
     */
    private String type = "h2-mem";

    /** Database name. Used by h2-mem, h2-file, postgres, mysql, mariadb, mongo. */
    private String name = "fogwall";

    /** File path. Used by h2-file (no extension). */
    private String path = "";

    // --- postgres / mysql / mariadb ---
    private String host = "localhost";

    /**
     * Default is PostgreSQL's port (5432). MySQL and MariaDB default to 3306 — set this explicitly when {@code type} is
     * {@code mysql} or {@code mariadb} unless the server actually listens on 5432.
     */
    private int port = 5432;

    private String username = "";
    private String password = "";

    /**
     * Full connection string. When non-blank, takes precedence over individual {@code host}/{@code port}/{@code name}
     * fields.
     *
     * <ul>
     *   <li><b>postgres</b> — JDBC URL, e.g. {@code jdbc:postgresql://host:5432/db?sslmode=verify-full}
     *   <li><b>mysql</b> — JDBC URL, e.g. {@code jdbc:mysql://host:3306/db?useSSL=true}
     *   <li><b>mariadb</b> — JDBC URL, e.g. {@code jdbc:mariadb://host:3306/db?useSsl=true}
     *   <li><b>mongo</b> — MongoDB connection URI, e.g. {@code mongodb://user:pass@host:27017/db?tls=true}
     * </ul>
     *
     * For mongo, the database name can be embedded in the URI path; the {@code name} field is used as a fallback when
     * the URI contains no path component.
     */
    private String url = "";

    /** HikariCP connection pool tuning. Applies to all JDBC backends (h2-mem, h2-file, postgres, mysql, mariadb). */
    private PoolConfig pool = new PoolConfig();
}
