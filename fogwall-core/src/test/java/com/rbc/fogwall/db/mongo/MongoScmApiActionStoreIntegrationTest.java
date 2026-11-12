package com.rbc.fogwall.db.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.rbc.fogwall.db.ScmApiActionPagingCheck;
import com.rbc.fogwall.db.model.ScmApiActionRecord;
import com.rbc.fogwall.db.model.ScmApiActionStatus;
import java.util.Date;
import java.util.UUID;
import org.bson.Document;
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

    private MongoClient client;
    private String databaseName;
    private MongoScmApiActionStore store;

    @BeforeEach
    void setUp() {
        client = MongoClients.create(MONGO.getConnectionString());
        databaseName = "testdb_" + UUID.randomUUID().toString().replace("-", "");
        store = new MongoScmApiActionStore(client, databaseName);
        store.initialize();
    }

    /** A record written while a refusal by policy had its own status reads as rejected, its reason untouched. */
    @Test
    void initialize_rewritesDeniedActionsAsRejected() {
        client.getDatabase(databaseName)
                .getCollection("scm_api_action_records")
                .insertOne(new Document("_id", "a1")
                        .append("timestamp", new Date())
                        .append("provider", "github")
                        .append("status", "DENIED")
                        .append("reason", "not allowlisted"));

        store.initialize();

        ScmApiActionRecord record = store.findById("a1").orElseThrow();
        assertEquals(ScmApiActionStatus.REJECTED, record.getStatus());
        assertEquals("not allowlisted", record.getReason());
    }

    @Test
    void find_afterACursor_walksEveryRecordOnce_andHonoursTheTimeWindow() {
        ScmApiActionPagingCheck.check(store);
    }
}
