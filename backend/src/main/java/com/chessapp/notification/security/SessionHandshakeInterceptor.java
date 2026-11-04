package com.chessapp.notification.security;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
/**
 * @author pari on 11/3/26
 */
@Component
@RequiredArgsConstructor
public class SessionHandshakeInterceptor
        implements HandshakeInterceptor {

    public static final String HTTP_SESSION_ID =
            SessionHandshakeInterceptor.class.getName() + ".sessionId";

    public static final String PLAYER_NAME =
            SessionHandshakeInterceptor.class.getName() + ".playerName";

    public static final String CSRF_TOKEN =
            SessionHandshakeInterceptor.class.getName() + ".csrfToken";

    private final CookieCsrfTokenRepository csrfTokenRepository;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Map<String, Object> attributes
    ) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        var httpRequest = servletRequest.getServletRequest();

        if (!(httpRequest.getUserPrincipal()
                instanceof UsernamePasswordAuthenticationToken auth)
                || !auth.isAuthenticated()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        HttpSession session = httpRequest.getSession(false);

        if (session == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        // Load the plain token from the cookie, rather than the
        // BREACH-protected token exposed as a request attribute.
        CsrfToken csrf = csrfTokenRepository.loadToken(httpRequest);

        if (csrf == null) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        // Copy only the handshake metadata needed by CONNECT.
        // Do not copy every HTTP session attribute into the socket.
        attributes.put(HTTP_SESSION_ID, session.getId());
        attributes.put(PLAYER_NAME, auth.getName());
        attributes.put(CSRF_TOKEN, csrf.getToken());

        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Exception exception
    ) {
        // No additional resources to release.
    }
}
