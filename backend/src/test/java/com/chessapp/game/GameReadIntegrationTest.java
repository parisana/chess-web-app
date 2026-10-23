package com.chessapp.game;

import com.chessapp.game.dto.GameResponse;
import com.chessapp.game.entity.Game;
import com.chessapp.game.service.GameService;
import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.service.PlayerService;
import com.chessapp.security.service.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * @author pari on 10/23/26
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GameReadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GameService gameService;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private JwtTokenService tokenService;

    private PlayerResponse white;
    private PlayerResponse black;
    private PlayerResponse outsider;
    private GameResponse game;

    @BeforeEach
    void setUp() {
        white = createPlayer();
        black = createPlayer();
        outsider = createPlayer();

        game = gameService.createGame(white.id(), black.id());
    }

    @Test
    void getGame_whiteParticipantCanReadState() throws Exception {
        mockMvc.perform(get("/api/v1/games/{gameId}", game.id())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(white.id())
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(game.id()))
                .andExpect(jsonPath("$.whitePlayerId").value(white.id()))
                .andExpect(jsonPath("$.blackPlayerId").value(black.id()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.currentFen").value(Game.INITIAL_FEN))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void getGame_blackParticipantCanReadState() throws Exception {
        mockMvc.perform(get("/api/v1/games/{gameId}", game.id())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(black.id())
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(game.id()));
    }

    @Test
    void getGame_nonParticipantIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/games/{gameId}", game.id())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(outsider.id())
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("GAME_ACCESS_DENIED"))
                .andExpect(jsonPath("$.currentFen").doesNotExist());
    }

    @Test
    void getGame_missingGameReturnsNotFound() throws Exception {
        // Generated game IDs are positive, making -1 a missing fixture
        // without relying on the database's current sequence value.
        mockMvc.perform(get("/api/v1/games/{gameId}", -1L)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(white.id())
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
    }

    @Test
    void getGame_missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/games/{gameId}", game.id()))
                .andExpect(status().isUnauthorized());
    }

    private String bearerToken(Long playerId) {
        // Exercise the configured JWT filter using a real signed token.
        return "Bearer " + tokenService.issueAccessToken(playerId)
                .getTokenValue();
    }

    private PlayerResponse createPlayer() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);

        return playerService.createPlayer(
                "read_" + suffix,
                suffix + "@example.com"
        );
    }
}