package com.chessapp.player;

import com.chessapp.game.service.GameCommandService;
import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.repository.PlayerRepository;
import com.chessapp.player.repository.PlayerStatisticsRepository;
import com.chessapp.player.service.PlayerService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import com.chessapp.support.TestSessions;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieSerializer;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


/**
 * @author pari on 11/2/26
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlayerStatisticsHttpIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PlayerService playerService;
    @Autowired private GameCommandService gameCommandService;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private PlayerStatisticsRepository statisticsRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private SessionRepository<? extends Session> sessionRepository;

    @Autowired private CookieSerializer cookieSerializer;

    private TestSessions sessions;

    private PlayerResponse player;

    @BeforeEach
    void setUp() {
        sessions = new TestSessions(sessionRepository, cookieSerializer);
        player = createPlayer();
    }
    @AfterEach
    void cleanUpSessions() {
        if (sessions != null) {
            sessions.close();
        }
    }

    @Test
    void returnsInitialStatisticsWithoutPrivateProfileFields()
            throws Exception {
        readStatistics(player.id())
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.playerId").value(player.id()))
                .andExpect(jsonPath("$.rating").value(1200))
                .andExpect(jsonPath("$.wins").value(0))
                .andExpect(jsonPath("$.draws").value(0))
                .andExpect(jsonPath("$.losses").value(0))
                .andExpect(jsonPath("$.gamesPlayed").value(0))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void completionIsReflectedForEachAuthenticatedPlayer()
            throws Exception {
        PlayerResponse opponent = createPlayer();

        var game = gameCommandService.createGame(
                player.id(),
                opponent.id()
        );

        gameCommandService.resign(
                game.id(),
                opponent.id(),
                game.version()
        );

        // Force subsequent reads to reload persisted state.
        entityManager.flush();
        entityManager.clear();

        readStatistics(player.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(player.id()))
                .andExpect(jsonPath("$.rating").value(1216))
                .andExpect(jsonPath("$.wins").value(1))
                .andExpect(jsonPath("$.losses").value(0))
                .andExpect(jsonPath("$.gamesPlayed").value(1));

        readStatistics(opponent.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(opponent.id()))
                .andExpect(jsonPath("$.rating").value(1184))
                .andExpect(jsonPath("$.wins").value(0))
                .andExpect(jsonPath("$.losses").value(1))
                .andExpect(jsonPath("$.gamesPlayed").value(1));
    }

    @Test
    void missingAuthenticationIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/players/me/statistics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionForDeletedPlayerIsRejected() throws Exception {
        Cookie cookie = sessions.forPlayer(player.id());

        statisticsRepository.deleteById(player.id());
        statisticsRepository.flush();

        playerRepository.deleteById(player.id());
        playerRepository.flush();

        entityManager.clear();

        mockMvc.perform(get("/api/v1/players/me/statistics")
                        .cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions readStatistics(long playerId)
            throws Exception {

        return mockMvc.perform(
                get("/api/v1/players/me/statistics")
                        .cookie(sessions.forPlayer(playerId))
        );
    }

    private PlayerResponse createPlayer() {
        String suffix = UUID.randomUUID().toString().replace("-", "");

        return playerService.createPlayer(
                "stats_" + suffix.substring(0, 20),
                suffix + "@example.com"
        );
    }
}
