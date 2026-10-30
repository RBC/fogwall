package com.rbc.fogwall.db;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** The MongoDB peer of the JDBC drop migration covered in {@code DatabaseMigratorTest}. */
@Testcontainers
@Tag("integration")
class MongoStoreFactoryIntegrationTest {

    @Container
    static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("docker.io/mongo:7.0").asCompatibleSubstituteFor("mongo"));

    @Test
    void dropRetiredCollections_dropsThemAndLeavesTheRest() {
        String dbName = "testdb_" + UUID.randomUUID().toString().replace("-", "");
        try (var factory = new MongoStoreFactory(MONGO.getConnectionString(), dbName)) {
            MongoDatabase database = factory.getMongoClient().getDatabase(dbName);
            for (String name : MongoStoreFactory.RETIRED_COLLECTIONS) {
                database.getCollection(name).insertOne(new Document("provider", "github"));
            }
            database.getCollection("push_records").insertOne(new Document("id", "kept"));

            factory.dropRetiredCollections();

            List<String> remaining = database.listCollectionNames().into(new ArrayList<>());
            assertEquals(List.of("push_records"), remaining);
        }
    }

    @Test
    void dropRetiredCollections_onAFreshDatabase_isANoOp() {
        String dbName = "testdb_" + UUID.randomUUID().toString().replace("-", "");
        try (var factory = new MongoStoreFactory(MONGO.getConnectionString(), dbName)) {
            assertDoesNotThrow(factory::dropRetiredCollections);
            assertDoesNotThrow(factory::dropRetiredCollections);
        }
    }
}
