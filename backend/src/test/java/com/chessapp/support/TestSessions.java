package com.chessapp.support;

import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieSerializer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates persisted authentication fixtures for feature HTTP tests.
 *
 * These fixtures bypass password authentication, but requests still pass
 * through the application's session filter and security filter chain.
 * Real login behavior is covered by SessionAuthenticationHttpIntegrationTest.
 */
public final class TestSessions implements AutoCloseable {

    private final SessionRepository<? extends Session> repository;
    private final CookieSerializer cookieSerializer;

    private final Map<Long, Fixture> fixtures =
            new ConcurrentHashMap<>();

    private final Set<String> createdSessionIds =
            ConcurrentHashMap.newKeySet();

    public TestSessions(
            SessionRepository<? extends Session> repository,
            CookieSerializer cookieSerializer
    ) {
        this.repository = repository;
        this.cookieSerializer = cookieSerializer;
    }

    public Cookie forPlayer(long playerId) {
        return fixture(playerId).cookie();
    }

    public String idForPlayer(long playerId) {
        return fixture(playerId).sessionId();
    }

    private Fixture fixture(long playerId) {
        if (playerId <= 0) {
            throw new IllegalArgumentException(
                    "A positive Player ID is required"
            );
        }

        return fixtures.computeIfAbsent(playerId, this::create);
    }

    private Fixture create(long playerId) {
        String sessionId = persist(repository, playerId);

        // Track it before cookie serialization so cleanup also works
        // if creating the cookie fails.
        createdSessionIds.add(sessionId);

        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        cookieSerializer.writeCookieValue(
                new CookieSerializer.CookieValue(
                        request,
                        response,
                        sessionId
                )
        );

        Cookie[] cookies = response.getCookies();

        if (cookies.length != 1) {
            throw new IllegalStateException(
                    "Expected exactly one session cookie"
            );
        }

        return new Fixture(sessionId, cookies[0]);
    }

    private <S extends Session> String persist(
            SessionRepository<S> sessions,
            long playerId
    ) {
        S session = sessions.createSession();

        var context = SecurityContextHolder.createEmptyContext();

        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        Long.toString(playerId),
                        null,
                        List.of()
                )
        );

        session.setAttribute(
                HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY,
                context
        );

        sessions.save(session);

        return session.getId();
    }

    @Override
    public void close() {
        createdSessionIds.forEach(repository::deleteById);
        createdSessionIds.clear();
        fixtures.clear();
    }

    private record Fixture(String sessionId, Cookie cookie) {
    }
}