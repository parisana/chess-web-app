package com.chessapp.auth;

import com.chessapp.auth.dto.RegisterRequest;
import com.chessapp.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "server.servlet.session.cookie.name=CHESS_SESSION",
        "server.servlet.session.cookie.secure=false",
        "spring.session.jdbc.cleanup-cron=-",
        "app.game.clock.timeout-scan-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionAuthenticationHttpIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private AuthService authService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private Long playerId;
    private String username;
    private String email;

    private static final String PASSWORD = "StrongPassword123!";

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 16);

        username = "session_" + suffix;
        email = suffix + "@example.com";

        playerId = authService.register(
                new RegisterRequest(username, email, PASSWORD)
        ).playerId();
    }

    @AfterEach
    void cleanUp() {
        if (playerId == null) {
            return;
        }

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    // Session attributes cascade when their parent is deleted.
                    jdbc.update("""
                            DELETE FROM SPRING_SESSION
                            WHERE PRINCIPAL_NAME = ?
                            """, playerId.toString());

                    jdbc.update("""
                            DELETE FROM auth_credentials
                            WHERE player_id = ?
                            """, playerId);

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

    @Test
    void loginPersistsAuthenticationForFreshCookieOnlyRequests()
            throws Exception {

        Cookie session = login(null);

        // These are separate requests with only the browser cookie:
        // no mock user, bearer JWT or MockHttpSession.
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(get("/api/v1/players/me").cookie(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(playerId))
                    .andExpect(jsonPath("$.username").value(username));
        }

        mvc.perform(get("/api/v1/players/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRequiresCsrfAndInvalidCredentialsRemainUnauthorized()
            throws Exception {

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(PASSWORD)))
                .andExpect(status().isForbidden());

        Cookie csrf = csrfCookie(null);

        mvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("WrongPassword123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_CREDENTIALS"));
    }

    @Test
    void authenticatedWritesStillRequireCsrf() throws Exception {
        Cookie session = login(null);

        String update = """
                {"username": "%s"}
                """.formatted(username + "_new");

        mvc.perform(patch("/api/v1/players/me")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isForbidden());

        Cookie csrf = csrfCookie(session);

        mvc.perform(patch("/api/v1/players/me")
                        .cookie(session, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username")
                        .value(username + "_new"));
    }

    @Test
    void anotherLoginChangesSessionIdAndRejectsOldCookie()
            throws Exception {

        Cookie oldSession = login(null);
        Cookie newSession = login(oldSession);

        assertNotEquals(
                oldSession.getValue(),
                newSession.getValue()
        );

        mvc.perform(get("/api/v1/players/me").cookie(oldSession))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/players/me").cookie(newSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(playerId));
    }

    @Test
    void logoutRequiresCsrfAndInvalidatesPersistedSession()
            throws Exception {

        Cookie session = login(null);

        mvc.perform(post("/api/v1/auth/logout").cookie(session))
                .andExpect(status().isForbidden());

        // A rejected logout must not invalidate the session.
        mvc.perform(get("/api/v1/players/me").cookie(session))
                .andExpect(status().isOk());

        Cookie csrf = csrfCookie(session);

        mvc.perform(post("/api/v1/auth/logout")
                        .cookie(session, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());

        // Reusing the actual old cookie must fail on the server.
        mvc.perform(get("/api/v1/players/me").cookie(session))
                .andExpect(status().isUnauthorized());

        assertEquals(0L, jdbc.queryForObject("""
                SELECT count(*) FROM SPRING_SESSION
                WHERE PRINCIPAL_NAME = ?
                """, Long.class, playerId.toString()));
    }

    private Cookie login(Cookie existingSession) throws Exception {
        Cookie csrf = csrfCookie(existingSession);

        MockHttpServletRequestBuilder request =
                post("/api/v1/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(PASSWORD));

        if (existingSession != null) {
            request.cookie(existingSession);
        }

        MvcResult result = mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(playerId))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        Cookie session = result.getResponse().getCookie("CHESS_SESSION");

        assertNotNull(session);
        assertTrue(session.isHttpOnly());

        return session;
    }

    private Cookie csrfCookie(Cookie session) throws Exception {
        MockHttpServletRequestBuilder request =
                get("/api/v1/auth/csrf");

        if (session != null) {
            request.cookie(session);
        }

        MvcResult result = mvc.perform(request)
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        Cookie csrf = result.getResponse().getCookie("XSRF-TOKEN");

        assertNotNull(csrf);
        assertFalse(csrf.isHttpOnly());

        return csrf;
    }

    private String credentials(String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }
}