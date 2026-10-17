package com.chessapp.auth.service;

import com.chessapp.auth.dto.LoginRequest;
import com.chessapp.auth.dto.LoginResponse;
import com.chessapp.security.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

/**
 * @author pari on 10/16/26
 */
@Service
@RequiredArgsConstructor
public class LoginService {

    private final AuthService authService;
    private final JwtTokenService jwtTokenService;

    public LoginResponse login(LoginRequest request) {
        var player = authService.authenticate(request);
        var token = jwtTokenService.issueAccessToken(player.id());

        long expiresIn = Duration.between(
                Objects.requireNonNull(token.getIssuedAt()),
                Objects.requireNonNull(token.getExpiresAt())
        ).toSeconds();

        return new LoginResponse(
                token.getTokenValue(),
                "Bearer",
                expiresIn
        );
    }
}
