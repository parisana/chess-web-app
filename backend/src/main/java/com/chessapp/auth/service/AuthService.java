package com.chessapp.auth.service;

import com.chessapp.auth.dto.LoginRequest;
import com.chessapp.auth.dto.RegisterRequest;
import com.chessapp.auth.dto.RegisterResponse;
import com.chessapp.auth.entity.AuthCredential;
import com.chessapp.auth.exception.InvalidCredentialsException;
import com.chessapp.auth.repository.AuthCredentialRepository;
import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.service.PlayerService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * @author pari on 10/16/26
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final PlayerService playerService;
    private final AuthCredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private String dummyPasswordHash;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        PlayerResponse player = playerService.createPlayer(
                request.username(),
                request.email()
        );

        String passwordHash = passwordEncoder.encode(
                request.password()
        );

        AuthCredential credential = new AuthCredential(
                player.id(),
                passwordHash
        );

        credentialRepository.save(credential);

        return new RegisterResponse(
                player.id(),
                player.username(),
                player.email()
        );
    }
    @Transactional(readOnly = true)
    public PlayerResponse authenticate(LoginRequest request) {
        var player = playerService.findByEmail(request.email());

        var credential = player.flatMap(
                value -> credentialRepository.findByPlayerId(value.id())
        );

        String hash = credential
                .map(AuthCredential::getPasswordHash)
                .orElse(dummyPasswordHash);

        boolean matches = passwordEncoder.matches(
                request.password(),
                hash
        );

        if (player.isEmpty() || credential.isEmpty() || !matches) {
            throw new InvalidCredentialsException();
        }

        return player.orElseThrow();
    }

    /// so that an unknown account still performs a password-hash comparison. This reduces the obvious difference between “no account found” and “incorrect password”; it does not guarantee identical request timings.
    @PostConstruct
    void initializeDummyPasswordHash() {
        dummyPasswordHash = passwordEncoder.encode(
                UUID.randomUUID().toString()
        );
    }
}
