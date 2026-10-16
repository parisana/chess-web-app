package com.chessapp.auth;

import com.chessapp.auth.dto.RegisterRequest;
import com.chessapp.auth.service.AuthService;
import com.chessapp.player.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author pari on 10/16/26
 * Test the registration transaction itself
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthRegistrationRollbackIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(AuthRegistrationRollbackIntegrationTest.class);

    @Autowired
    private AuthService authService;

    @Autowired
    private PlayerRepository playerRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @Test
    void register_rollsBackPlayerWhenPasswordEncodingFails() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);

        String username = "rollback_" + suffix;
        String email = suffix + "@example.com";
        String password = "StrongPassword123!";

        RegisterRequest request = new RegisterRequest(
                username,
                email,
                password
        );

        IllegalStateException failure =
                new IllegalStateException("Simulated encoding failure");

        when(passwordEncoder.encode(password))
                .thenThrow(failure);

        try {
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> authService.register(request)
            );

            assertSame(failure, thrown);
            verify(passwordEncoder).encode(password);

            assertFalse(playerRepository.existsByUsername(username));
            assertFalse(playerRepository.existsByEmail(email));
        } finally {
            // Remove this test's player if a transaction regression [ex. removal of @Transactional from authService.register method]
            // caused it to remain committed.
            playerRepository.findByUsername(username)
                    .ifPresent(player ->
                            playerRepository.deleteById(player.getId())
                    );
        }
    }
}
