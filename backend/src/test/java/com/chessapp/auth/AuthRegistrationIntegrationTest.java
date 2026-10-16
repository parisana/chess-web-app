package com.chessapp.auth;

import com.chessapp.auth.dto.RegisterRequest;
import com.chessapp.auth.dto.RegisterResponse;
import com.chessapp.auth.entity.AuthCredential;
import com.chessapp.auth.repository.AuthCredentialRepository;
import com.chessapp.auth.service.AuthService;
import com.chessapp.player.repository.PlayerRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/16/26
 */
@SpringBootTest
@ActiveProfiles("test")
// @Transactional on a test normally rolls back work participating in its transaction.
// however, with a real HTTP server using RANDOM_PORT or DEFINED_PORT, server writes run in a separate transaction and won’t roll back with the test
@Transactional
public class AuthRegistrationIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private AuthCredentialRepository credentialRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void register_persistsPlayerAndEncodedCredentials() {

        RegisterRequest request = new RegisterRequest(
                "integrationplayer",
                "integration@example.com",
                "StrongPassword123!"
        );

        RegisterResponse response = authService.register(request);
        /*Flushing makes pending persistence failures visible; clearing ensures subsequent queries load database state.
        Spring recommends flushing ORM work to avoid false-positive tests.*/
        entityManager.flush();
        entityManager.clear();

        assertNotNull(response.playerId());

        var player = playerRepository.findById(response.playerId())
                .orElseThrow();

        AuthCredential credential = credentialRepository
                .findByPlayerId(player.getId())
                .orElseThrow();

        assertEquals(request.username(), player.getUsername());
        assertEquals(request.email(), player.getEmail());
        assertEquals(response.playerId(), credential.getPlayerId());
        assertNotNull(player.getCreatedAt());
        assertNotNull(player.getUpdatedAt());
        assertNotNull(credential.getCreatedAt());
        assertNotNull(credential.getUpdatedAt());

        assertNotEquals(
                request.password(),
                credential.getPasswordHash()
        );

        assertTrue(passwordEncoder.matches(
                request.password(),
                credential.getPasswordHash()
        ));
    }

}

