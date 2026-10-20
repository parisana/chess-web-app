package com.chessapp.auth.service;

import com.chessapp.auth.dto.LoginRequest;
import com.chessapp.auth.dto.RegisterRequest;
import com.chessapp.auth.dto.RegisterResponse;
import com.chessapp.auth.entity.AuthCredential;
import com.chessapp.auth.exception.InvalidCredentialsException;
import com.chessapp.auth.repository.AuthCredentialRepository;
import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.service.PlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        PlayerResponse player = stubPlayerResponse(1L);

        when(playerService.createPlayer(
                request.username(),
                request.email()
        )).thenReturn(player);

        when(passwordEncoder.encode(request.password()))
                .thenReturn("encoded-password");

        RegisterResponse response = authService.register(request);

        assertEquals(request.username(), response.username());
        assertEquals(request.email(), response.email());
        assertEquals(1L, response.playerId());

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
        PlayerResponse player = stubPlayerResponse(1L);

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

    private PlayerResponse stubPlayerResponse(long id) {
        return new PlayerResponse(
                id,
                request.username(),
                request.email()
        );
    }

    @Test
    void authenticate_returnsPlayerForValidCredentials() {
        PlayerResponse player = stubPlayerResponse(1L);

        when(playerService.findByEmail(request.email()))
                .thenReturn(Optional.of(player));

        when(credentialRepository.findByPlayerId(1L))
                .thenReturn(Optional.of(
                        new AuthCredential(1L, "stored-hash")
                ));

        when(passwordEncoder.matches(request.password(), "stored-hash"))
                .thenReturn(true);

        PlayerResponse result = authService.authenticate(
                new LoginRequest(request.email(), request.password())
        );

        assertEquals(player, result);
        // verifies the encoder.matches() was called exactly once
        verify(passwordEncoder).matches(
                request.password(),
                "stored-hash"
        );
    }

    @Test
    void authenticate_rejectsUnknownEmail() {
        when(passwordEncoder.encode(anyString()))
                .thenReturn("dummy-hash");

        authService.initializeDummyPasswordHash();
        when(playerService.findByEmail(request.email()))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.authenticate(
                        new LoginRequest(request.email(), request.password())
                )
        );

        verify(passwordEncoder).matches(
                request.password(),
                "dummy-hash"
        );

        verifyNoInteractions(credentialRepository);
    }

    @Test
    void authenticate_rejectsMissingLocalCredentials() {
        when(passwordEncoder.encode(anyString()))
                .thenReturn("dummy-hash");

        authService.initializeDummyPasswordHash();

        PlayerResponse player = stubPlayerResponse(1L);

        when(playerService.findByEmail(request.email()))
                .thenReturn(Optional.of(player));

        when(credentialRepository.findByPlayerId(player.id()))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.authenticate(
                        new LoginRequest(
                                request.email(),
                                request.password()
                        )
                )
        );

        verify(passwordEncoder).matches(
                request.password(),
                "dummy-hash"
        );
    }

    @Test
    void authenticate_rejectsIncorrectPassword() {
        PlayerResponse player = stubPlayerResponse(1L);

        when(playerService.findByEmail(request.email()))
                .thenReturn(Optional.of(player));

        when(credentialRepository.findByPlayerId(1L))
                .thenReturn(Optional.of(
                        new AuthCredential(1L, "stored-hash")
                ));

        when(passwordEncoder.matches("wrong-password", "stored-hash"))
                .thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.authenticate(
                        new LoginRequest(request.email(), "wrong-password")
                )
        );
    }
}