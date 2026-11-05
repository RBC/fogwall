package com.rbc.fogwall.permission;

import com.mongodb.client.MongoClients;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Tag("integration")
class MongoRoleChangeStoreIntegrationTest extends RoleChangeStoreContract {

    @Container
    static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("docker.io/mongo:7.0").asCompatibleSubstituteFor("mongo"));

    private MongoRoleChangeStore store;

    @BeforeEach
    void setUp() {
        store = new MongoRoleChangeStore(
                MongoClients.create(MONGO.getConnectionString()),
                "testdb_" + UUID.randomUUID().toString().replace("-", ""));
        store.initialize();
    }

    @Override
    protected RoleChangeStore store() {
        return store;
    }
}
