package com.chessapp.player;

import com.chessapp.game.service.GameService;
import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.repository.PlayerRepository;
import com.chessapp.player.repository.PlayerStatisticsRepository;
import com.chessapp.player.service.PlayerService;
import com.chessapp.security.service.JwtTokenService;
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
    @Autowired private GameService gameService;
    @Autowired private JwtTokenService tokenService;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private PlayerStatisticsRepository statisticsRepository;
    @Autowired private EntityManager entityManager;

    private PlayerResponse player;

    @BeforeEach
    void setUp() {
        player = createPlayer();
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

        var game = gameService.createGame(
                player.id(),
                opponent.id()
        );

        gameService.resign(
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
    void tokenForDeletedPlayerIsRejected() throws Exception {
        String token = bearer(player.id());

        statisticsRepository.deleteById(player.id());
        statisticsRepository.flush();

        playerRepository.deleteById(player.id());
        playerRepository.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/players/me/statistics")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions readStatistics(long playerId)
            throws Exception {
        return mockMvc.perform(
                get("/api/v1/players/me/statistics")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearer(playerId)
                        )
        );
    }

    private String bearer(long playerId) {
        return "Bearer " + tokenService.issueAccessToken(playerId)
                .getTokenValue();
    }

    private PlayerResponse createPlayer() {
        String suffix = UUID.randomUUID().toString().replace("-", "");

        return playerService.createPlayer(
                "stats_" + suffix.substring(0, 20),
                suffix + "@example.com"
        );
    }
}
