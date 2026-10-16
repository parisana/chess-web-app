package com.chessapp.auth.service;

import com.chessapp.auth.dto.RegisterRequest;
import com.chessapp.auth.dto.RegisterResponse;
import com.chessapp.auth.entity.AuthCredential;
import com.chessapp.auth.repository.AuthCredentialRepository;
import com.chessapp.player.entity.Player;
import com.chessapp.player.service.PlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author pari on 10/16/26
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private PlayerService playerService;

    @Mock
    private AuthCredentialRepository credentialRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest request;

    @BeforeEach
    void setUp() {
        request = new RegisterRequest(
                "chessplayer1",
                "player1@example.com",
                "StrongPassword123!"
        );
    }

    @Test
    void register_createsPlayerAndEncodedCredentials() {
        Player player = mock(Player.class);
        when(player.getId()).thenReturn(1L);
        when(player.getUsername()).thenReturn(request.username());
        when(player.getEmail()).thenReturn(request.email());

        when(playerService.createPlayer(
                request.username(),
                request.email()
        )).thenReturn(player);

        when(passwordEncoder.encode(request.password()))
                .thenReturn("encoded-password");

        RegisterResponse response = authService.register(request);

        assertEquals(request.username(), response.username());
        assertEquals(request.email(), response.email());

        ArgumentCaptor<AuthCredential> captor =
                ArgumentCaptor.forClass(AuthCredential.class);

        // verifies the save method was called! while we capture the argument for assertion.
        verify(credentialRepository).save(captor.capture());

        assertEquals(
                "encoded-password",
                captor.getValue().getPasswordHash()
        );
        assertEquals(
                1L,
                captor.getValue().getPlayerId()
        );
    }

//    verifies exception propagation, not transaction rollback. Rollback requires a Spring-managed transaction and an actual database integration test.
    @Test
    void register_propagatesCredentialPersistenceFailure() {
        Player player = new Player(
                request.username(),
                request.email()
        );

        when(playerService.createPlayer(
                request.username(),
                request.email()
        )).thenReturn(player);

        when(passwordEncoder.encode(request.password()))
                .thenReturn("encoded-password");

        when(credentialRepository.save(any(AuthCredential.class)))
                .thenThrow(new IllegalStateException("Database failure"));

        assertThrows(
                IllegalStateException.class,
                () -> authService.register(request)
        );
    }
}