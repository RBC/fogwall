package com.rbc.fogwall.db;

import com.mongodb.client.MongoClients;
import com.rbc.fogwall.db.jdbc.DataSourceFactory;
import com.rbc.fogwall.db.jdbc.JdbcPushStore;
import com.rbc.fogwall.db.mongo.MongoPushStore;
import javax.sql.DataSource;

/**
 * Factory for creating {@link PushStore} instances from simple configuration parameters. Handles initialization
 * automatically.
 */
public final class PushStoreFactory {

    private PushStoreFactory() {}

    /** Create an H2 in-memory store (SQL with schema, but data is lost on restart). */
    public static PushStore h2InMemory() {
        return h2InMemory("fogwall");
    }

    /** Create an H2 in-memory store with a custom database name. */
    public static PushStore h2InMemory(String dbName) {
        JdbcPushStore store = new JdbcPushStore(DataSourceFactory.h2InMemory(dbName));
        store.initialize();
        return store;
    }

    /** Create an H2 file-based store. */
    public static PushStore h2File(String filePath) {
        JdbcPushStore store = new JdbcPushStore(DataSourceFactory.h2File(filePath));
        store.initialize();
        return store;
    }

    /** Create a PostgreSQL store. */
    public static PushStore postgres(String host, int port, String database, String username, String password) {
        JdbcPushStore store = new JdbcPushStore(DataSourceFactory.postgres(host, port, database, username, password));
        store.initialize();
        return store;
    }

    /** Create a MongoDB store. */
    public static PushStore mongo(String connectionString, String databaseName) {
        MongoPushStore store = new MongoPushStore(MongoClients.create(connectionString), databaseName);
        store.initialize();
        return store;
    }

    /** Create a store from a JDBC URL (auto-detects H2, SQLite, or Postgres). */
    public static PushStore fromJdbcUrl(String jdbcUrl, String username, String password) {
        JdbcPushStore store = new JdbcPushStore(DataSourceFactory.fromUrl(jdbcUrl, username, password));
        store.initialize();
        return store;
    }

    /** Create a store from an already-configured {@link DataSource} (shared pool use case). */
    public static PushStore fromDataSource(DataSource dataSource) {
        JdbcPushStore store = new JdbcPushStore(dataSource);
        store.initialize();
        return store;
    }
}
