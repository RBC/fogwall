package com.rbc.fogwall.dashboard.session;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Tag("integration")
class RedisSessionRepositoryIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> VALKEY =
            new GenericContainer<>(DockerImageName.parse("docker.io/valkey/valkey:8")).withExposedPorts(6379);

    private LettuceConnectionFactory factory;
    private RedisIndexedSessionRepository repo;

    @BeforeEach
    void setUp() {
        var standalone = new RedisStandaloneConfiguration(VALKEY.getHost(), VALKEY.getMappedPort(6379));
        factory = new LettuceConnectionFactory(standalone, LettuceClientConfiguration.defaultConfiguration());
        factory.afterPropertiesSet();

        var template = new RedisTemplate<String, Object>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(RedisSerializer.string());
        template.setHashKeySerializer(RedisSerializer.string());
        template.afterPropertiesSet();

        repo = new RedisIndexedSessionRepository(template);
        repo.setDefaultMaxInactiveInterval(Duration.ofMinutes(30));
    }

    @AfterEach
    void tearDown() {
        factory.destroy();
    }

    @Test
    void createSession_hasConfiguredTimeout() {
        var s = repo.createSession();
        assertEquals(Duration.ofMinutes(30), s.getMaxInactiveInterval());
    }

    @Test
    void save_then_findById_roundTripsAttributes() {
        var s = repo.createSession();
        s.setAttribute("username", "alice");
        s.setAttribute("role-count", 3);
        repo.save(s);

        var loaded = repo.findById(s.getId());
        assertNotNull(loaded);
        assertEquals(s.getId(), loaded.getId());
        assertEquals("alice", loaded.getAttribute("username"));
        assertEquals(Integer.valueOf(3), loaded.getAttribute("role-count"));
    }

    @Test
    void findById_unknownId_returnsNull() {
        assertNull(repo.findById("does-not-exist"));
    }

    @Test
    void deleteById_removesSession() {
        var s = repo.createSession();
        s.setAttribute("k", "v");
        repo.save(s);
        assertNotNull(repo.findById(s.getId()));

        repo.deleteById(s.getId());
        assertNull(repo.findById(s.getId()));
    }
}
