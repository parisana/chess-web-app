package com.chessapp.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 11/3/26
 */
@SpringBootTest
@ActiveProfiles("test")
class JdbcSessionPersistenceIntegrationTest {

    @Autowired
    private JdbcIndexedSessionRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<String> sessionIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        sessionIds.forEach(repository::deleteById);
    }

    @Test
    void sessionAttributesSurviveReloadAndDeletionCascades() {
        String id = saveFixture(repository);

        // A fresh repository read checks database-backed serialization,
        // rather than inspecting the original in-memory session object.
        Session restored = repository.findById(id);

        assertNotNull(restored);
        assertEquals(
                Long.valueOf(42L),
                restored.<Long>getAttribute("fixturePlayerId")
        );

        assertEquals(1L, attributeCount(id));

        repository.deleteById(id);

        assertNull(repository.findById(id));
        assertEquals(0L, attributeCount(id));
    }

    @Test
    void expiredSessionIsRejectedWithoutWaitingForScheduledCleanup() {
        String id = saveFixture(repository);

        // Force expiry using timestamps far in the past.
        // No sleep or changes to the application's Clock bean are needed.
        jdbc.update("""
                UPDATE SPRING_SESSION
                SET LAST_ACCESS_TIME = 0,
                    EXPIRY_TIME = 1
                WHERE SESSION_ID = ?
                """, id);

        assertNull(repository.findById(id));
    }

    /**
     * Uses Spring Session's public interface because the JDBC implementation
     * returns an internal session type.
     */
    private <S extends Session> String saveFixture(
            SessionRepository<S> sessions
    ) {
        S session = sessions.createSession();

        sessionIds.add(session.getId());

        session.setMaxInactiveInterval(Duration.ofHours(1));

        // A serializable fixture attribute, not actual authentication.
        session.setAttribute("fixturePlayerId", 42L);

        sessions.save(session);

        return session.getId();
    }

    private long attributeCount(String sessionId) {
        Long count = jdbc.queryForObject("""
                SELECT count(*)
                FROM SPRING_SESSION_ATTRIBUTES attributes
                JOIN SPRING_SESSION sessions
                  ON sessions.PRIMARY_ID = attributes.SESSION_PRIMARY_ID
                WHERE sessions.SESSION_ID = ?
                """, Long.class, sessionId);

        return count == null ? 0L : count;
    }
}
