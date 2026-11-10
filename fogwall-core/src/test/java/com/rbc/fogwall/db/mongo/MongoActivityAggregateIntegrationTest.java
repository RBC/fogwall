package com.rbc.fogwall.db.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.rbc.fogwall.db.ActivityAggregateContract;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.ScmApiActionStore;
import com.rbc.fogwall.db.ScmApiEntityStore;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Tag("integration")
class MongoActivityAggregateIntegrationTest extends ActivityAggregateContract {

    @Container
    static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("docker.io/mongo:7.0").asCompatibleSubstituteFor("mongo"));

    private MongoPushStore pushStore;
    private MongoScmApiActionStore actionStore;
    private MongoScmApiEntityStore entityStore;

    @BeforeEach
    void setUp() {
        MongoClient client = MongoClients.create(MONGO.getConnectionString());
        String database = "testdb_" + UUID.randomUUID().toString().replace("-", "");
        pushStore = new MongoPushStore(client, database);
        pushStore.initialize();
        actionStore = new MongoScmApiActionStore(client, database);
        actionStore.initialize();
        entityStore = new MongoScmApiEntityStore(client, database);
        entityStore.initialize();
    }

    @Override
    protected PushStore pushStore() {
        return pushStore;
    }

    @Override
    protected ScmApiActionStore actionStore() {
        return actionStore;
    }

    @Override
    protected ScmApiEntityStore entityStore() {
        return entityStore;
    }
}
