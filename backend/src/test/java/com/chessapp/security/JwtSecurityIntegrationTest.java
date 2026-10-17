package com.chessapp.security;

import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.service.PlayerService;
import com.chessapp.security.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @author pari on 10/16/26
 */
@SpringBootTest
@AutoConfigureMockMvc // send actual signed tokens through the filter chain
@ActiveProfiles("test")
class JwtSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private Clock clock;

    @Autowired
    private PlayerService playerService;

    @Value("${app.jwt.issuer}")
    private String issuer;

    @Test
    void currentPlayer_rejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/players/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void currentPlayer_acceptsValidToken() throws Exception {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);

        PlayerResponse player = playerService.createPlayer(
                "player_" + suffix,
                suffix + "@example.com"
        );

        String token = tokenService.issueAccessToken(player.id())
                .getTokenValue();

        mockMvc.perform(get("/api/v1/players/me")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(player.id().intValue()))
                .andExpect(jsonPath("$.username").value(player.username()))
                .andExpect(jsonPath("$.email").value(player.email()));
    }

    @Test
    void currentPlayer_rejectsMalformedToken() throws Exception {
        expectUnauthorized("not-a-jwt");
    }

    @Test
    void currentPlayer_rejectsExpiredToken() throws Exception {
        Instant now = clock.instant();

        String token = sign(
                issuer,
                now.minusSeconds(600),
                now.minusSeconds(120)
        );

        expectUnauthorized(token);
    }

    @Test
    void currentPlayer_rejectsWrongIssuer() throws Exception {
        Instant now = clock.instant();

        String token = sign(
                "another-application",
                now,
                now.plusSeconds(300)
        );

        expectUnauthorized(token);
    }

    @Test
    void currentPlayer_rejectsInvalidSignature() throws Exception {
        String token = tokenService.issueAccessToken(42L)
                .getTokenValue();

        String[] parts = token.split("\\.");

        // Change the first signature character, leaving the claims intact.
        char replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';

        String alteredSignature =
                replacement + parts[2].substring(1);

        expectUnauthorized(
                parts[0] + "." + parts[1] + "." + alteredSignature
        );
    }

    private String sign(
            String tokenIssuer,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(tokenIssuer)
                .subject("42")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims
                )
        ).getTokenValue();
    }

    private void expectUnauthorized(String token) throws Exception {
        mockMvc.perform(get("/api/v1/players/me")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isUnauthorized());
    }
}