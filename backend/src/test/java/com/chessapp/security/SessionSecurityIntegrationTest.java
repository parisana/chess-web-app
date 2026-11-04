package com.chessapp.security;

import com.chessapp.player.service.PlayerService;
import com.chessapp.support.TestSessions;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "server.servlet.session.cookie.name=CHESS_SESSION",
        "spring.session.jdbc.cleanup-cron=-",
        "app.game.clock.timeout-scan-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PlayerService playerService;
    @Autowired private SessionRepository<? extends Session> sessionRepository;
    @Autowired private CookieSerializer cookieSerializer;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private TestSessions sessions;
    private Long playerId;

    @BeforeEach
    void setUp() {
        sessions = new TestSessions(sessionRepository, cookieSerializer);

        String suffix = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 16);

        playerId = playerService.createPlayer(
                "security_" + suffix,
                suffix + "@example.com"
        ).id();
    }

    @AfterEach
    void cleanUp() {
        if (sessions != null) {
            sessions.close();
        }

        if (playerId != null) {
            deletePlayer();
        }
    }

    @Test
    void rejectsMissingSession() throws Exception {
        mockMvc.perform(get("/api/v1/players/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsPersistedAuthenticatedSession() throws Exception {
        mockMvc.perform(get("/api/v1/players/me")
                        .cookie(sessions.forPlayer(playerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(playerId));
    }

    @Test
    void rejectsUnknownSessionCookie() throws Exception {
        mockMvc.perform(get("/api/v1/players/me")
                        .cookie(new Cookie(
                                "CHESS_SESSION",
                                "unknown-session"
                        )))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bearerHeaderDoesNotEstablishAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/players/me")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer obsolete-credential"
                        ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredSessionIsRejectedBeforeScheduledCleanup()
            throws Exception {

        Cookie cookie = sessions.forPlayer(playerId);

        // Commit the expiry change before making the HTTP request.
        jdbc.update("""
                UPDATE SPRING_SESSION
                SET LAST_ACCESS_TIME = 0,
                    EXPIRY_TIME = 1
                WHERE SESSION_ID = ?
                """, sessions.idForPlayer(playerId));

        mockMvc.perform(get("/api/v1/players/me").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionForDeletedPlayerIsRejected() throws Exception {
        Cookie cookie = sessions.forPlayer(playerId);

        deletePlayer();

        mockMvc.perform(get("/api/v1/players/me").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    private void deletePlayer() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    jdbc.update("""
                            DELETE FROM player_statistics
                            WHERE player_id = ?
                            """, playerId);

                    jdbc.update(
                            "DELETE FROM players WHERE id = ?",
                            playerId
                    );
                });
    }
}