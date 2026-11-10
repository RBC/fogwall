package com.rbc.fogwall.db.mongo;

import com.mongodb.client.MongoClients;
import com.rbc.fogwall.db.ScmApiActionPagingCheck;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Tag("integration")
class MongoScmApiActionStoreIntegrationTest {

    @Container
    static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("docker.io/mongo:7.0").asCompatibleSubstituteFor("mongo"));

    private MongoScmApiActionStore store;

    @BeforeEach
    void setUp() {
        store = new MongoScmApiActionStore(
                MongoClients.create(MONGO.getConnectionString()),
                "testdb_" + UUID.randomUUID().toString().replace("-", ""));
        store.initialize();
    }

    @Test
    void find_afterACursor_walksEveryRecordOnce_andHonoursTheTimeWindow() {
        ScmApiActionPagingCheck.check(store);
    }
}
