package com.chessapp.auth.service;

import com.chessapp.auth.dto.LoginRequest;
import com.chessapp.player.dto.PlayerResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * @author pari on 11/03/26
 */
@Service
@RequiredArgsConstructor
public class LoginService {

    private final AuthService authService;
    private final SessionAuthenticationStrategy loginSessionStrategy;
    private final HttpSessionSecurityContextRepository
            securityContextRepository;

    /**
     * Verify credentials before changing session state, then explicitly
     * persist the authenticated context for subsequent requests.
     */
    public PlayerResponse login(
            LoginRequest credentials,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        PlayerResponse player = authService.authenticate(credentials);

        // Keep the canonical Player ID as the principal name.
        // Passwords and the complete profile are not stored in the session.
        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        player.id().toString(),
                        null,
                        List.of()
                );

        loginSessionStrategy.onAuthentication(
                authentication,
                request,
                response
        );

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);

        securityContextRepository.saveContext(
                context,
                request,
                response
        );

        return player;
    }
}
