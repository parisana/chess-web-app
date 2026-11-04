package com.chessapp.player;

import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.repository.PlayerRepository;
import com.chessapp.player.repository.PlayerStatisticsRepository;
import com.chessapp.player.service.PlayerService;
import com.chessapp.support.TestSessions;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
/**
 * @author pari on 10/23/26
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlayerUpdateIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private PlayerStatisticsRepository playerStatisticsRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private EntityManager entityManager;
    @Autowired private SessionRepository<? extends Session> sessionRepository;

    @Autowired private CookieSerializer cookieSerializer;

    private PlayerResponse player;
    private Cookie sessionCookie;
    private TestSessions sessions;
    @BeforeEach
    void setUp() {
        sessions = new TestSessions(sessionRepository, cookieSerializer);
        player = createPlayer();
        sessionCookie = sessions.forPlayer(player.id());
    }

    @AfterEach
    void cleanUpSessions() {
        if (sessions != null) {
            sessions.close();
        }
    }

    @Test
    void update_persistsUsernameAndPreservesIdentity() throws Exception {
        String username = "renamed_" + uniqueSuffix();

        doUpdatePlayer(username)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(player.id()))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(player.email()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // Reload from PostgreSQL instead of inspecting the managed object.
        entityManager.flush();
        entityManager.clear();

        var persisted = playerRepository.findById(player.id())
                .orElseThrow();

        assertAll(
                () -> assertEquals(username, persisted.getUsername()),
                () -> assertEquals(player.email(), persisted.getEmail())
        );

        // Renaming does not change the Player ID stored in authentication.
        mockMvc.perform(get("/api/v1/players/me")
                .cookie(sessionCookie)
                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    void update_sameUsernameSucceedsWithoutChangingTimestamp()
            throws Exception {

        entityManager.flush();
        entityManager.clear();

        var originalUpdatedAt = playerRepository.findById(player.id())
                .orElseThrow()
                .getUpdatedAt();

        doUpdatePlayer(player.username())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username")
                        .value(player.username()));

        entityManager.flush();
        entityManager.clear();

        var persisted = playerRepository.findById(player.id())
                .orElseThrow();

        assertEquals(originalUpdatedAt, persisted.getUpdatedAt());
    }

    @Test
    void update_duplicateUsernameReturnsConflict() throws Exception {
        var otherPlayer = createPlayer();

        doUpdatePlayer(otherPlayer.username())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("USERNAME_ALREADY_EXISTS"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "ab",
            "1234567890123456789012345678901"
    })
    void update_invalidUsernameReturnsValidationError(String username)
            throws Exception {

        doUpdatePlayer(username)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(hasItem("username")));

        entityManager.clear();

        assertEquals(
                player.username(),
                playerRepository.findById(player.id())
                        .orElseThrow()
                        .getUsername()
        );
    }

    @Test
    void update_changesOnlyAuthenticatedPlayer() throws Exception {
        var otherPlayer = createPlayer();
        String username = "owner_" + uniqueSuffix();

        doUpdatePlayer(username)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(player.id()));

        entityManager.flush();
        entityManager.clear();

        assertAll(
                () -> assertEquals(
                        username,
                        playerRepository.findById(player.id())
                                .orElseThrow()
                                .getUsername()
                ),
                () -> assertEquals(
                        otherPlayer.username(),
                        playerRepository.findById(otherPlayer.id())
                                .orElseThrow()
                                .getUsername()
                )
        );
    }

    @Test
    void update_missingSessionReturnsUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/v1/players/me")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"username": "new_username"}
                            """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void update_deletedPlayerReturnsUnauthorized() throws Exception {
        // Remove dependent statistics before deleting the fixture account.
        playerStatisticsRepository.deleteById(player.id());
        playerStatisticsRepository.flush();

        playerRepository.deleteById(player.id());
        playerRepository.flush();
        entityManager.clear();

        // The session still exists, but its Player account has been deleted.
        doUpdatePlayer("new_username")
                .andExpect(status().isUnauthorized());
    }

    private ResultActions doUpdatePlayer(String username) throws Exception {
        // Fixtures contain only generated usernames or simple validation inputs.
        String body = username == null
                ? """
                  {"username": null}
                  """
                : """
                  {"username": "%s"}
                  """.formatted(username);

        return mockMvc.perform(patch("/api/v1/players/me")
                        .cookie(sessionCookie)
                        .with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private PlayerResponse createPlayer() {
        String suffix = uniqueSuffix();

        return playerService.createPlayer(
                "profile_" + suffix,
                suffix + "@example.com"
        );
    }

    private static String uniqueSuffix() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);
    }
}
