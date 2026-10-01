package com.rbc.fogwall.db.mongo;

import com.mongodb.client.MongoClients;
import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.FetchStoreContract;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Tag("integration")
class MongoFetchStoreIntegrationTest extends FetchStoreContract {

    @Container
    static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("docker.io/mongo:7.0").asCompatibleSubstituteFor("mongo"));

    MongoFetchStore store;

    @BeforeEach
    void setUp() {
        store = new MongoFetchStore(
                MongoClients.create(MONGO.getConnectionString()),
                "testdb_" + UUID.randomUUID().toString().replace("-", ""));
        store.initialize();
    }

    @Override
    protected FetchStore store() {
        return store;
    }
}
