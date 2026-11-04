package com.chessapp.notification.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * @author pari on 10/29/26
 * check expiry of delivery when a subscribed client remains silent
 */
@Component
@RequiredArgsConstructor
public class WebSocketSessionAccess {

    private final SessionRepository<? extends Session> sessionRepository;

    private final Map<String, Binding> bindings =
            new ConcurrentHashMap<>();

    /**
     * Bind a WebSocket connection to the exact authenticated HTTP session.
     * Reauthentication or logout must not silently transfer an old socket
     * to a different session, even when the Player ID is unchanged.
     */
    public void register(
            String webSocketSessionId,
            String httpSessionId,
            String playerName
    ) {
        if (webSocketSessionId == null || webSocketSessionId.isBlank()
                || httpSessionId == null || httpSessionId.isBlank()
                || !isCanonicalPlayerName(playerName)) {
            throw new BadCredentialsException(
                    "An authenticated Player session is required"
            );
        }

        Binding binding = new Binding(httpSessionId, playerName);

        if (!isValid(binding)) {
            throw new BadCredentialsException(
                    "The HTTP session is no longer authenticated"
            );
        }

        if (bindings.putIfAbsent(webSocketSessionId, binding) != null) {
            throw new BadCredentialsException(
                    "The WebSocket session is already registered"
            );
        }
    }

    public boolean canAccess(
            String webSocketSessionId,
            String playerName
    ) {
        if (webSocketSessionId == null) {
            return false;
        }

        Binding binding = bindings.get(webSocketSessionId);

        return binding != null
                && binding.playerName().equals(playerName)
                && canReceive(webSocketSessionId);
    }

    public boolean canReceive(String webSocketSessionId) {
        if (webSocketSessionId == null) {
            return false;
        }

        Binding binding = bindings.get(webSocketSessionId);

        if (binding == null) {
            return false;
        }

        if (!isValid(binding)) {
            bindings.remove(webSocketSessionId, binding);
            return false;
        }

        return true;
    }

    private boolean isValid(Binding binding) {
        // This is a repository read, not a session-activity update.
        // Socket traffic must not silently extend the inactivity timeout.
        Session session = sessionRepository.findById(
                binding.httpSessionId()
        );

        if (session == null || session.isExpired()) {
            return false;
        }

        Object attribute = session.getAttribute(
                HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY
        );

        if (!(attribute instanceof SecurityContext context)) {
            return false;
        }

        return context.getAuthentication()
                instanceof UsernamePasswordAuthenticationToken auth
                && auth.isAuthenticated()
                && binding.playerName().equals(auth.getName());
    }

    private static boolean isCanonicalPlayerName(String name) {
        if (name == null) {
            return false;
        }

        try {
            long id = Long.parseLong(name);

            return id > 0 && Long.toString(id).equals(name);
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        // Spring may publish more than one disconnect notification.
        bindings.remove(event.getSessionId());
    }

    private record Binding(
            String httpSessionId,
            String playerName
    ) {
    }
}