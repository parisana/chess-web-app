package com.chessapp.auth.service;

import com.chessapp.auth.dto.RegisterRequest;
import com.chessapp.auth.dto.RegisterResponse;
import com.chessapp.auth.entity.AuthCredential;
import com.chessapp.auth.repository.AuthCredentialRepository;
import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author pari on 10/16/26
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final PlayerService playerService;
    private final AuthCredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;

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
}
