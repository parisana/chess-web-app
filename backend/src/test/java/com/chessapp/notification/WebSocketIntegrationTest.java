package com.chessapp.notification;

import com.chessapp.auth.dto.RegisterRequest;
import com.chessapp.auth.service.AuthService;
import com.chessapp.game.dto.GameResponse;
import com.chessapp.game.entity.GameStatus;
import com.chessapp.game.event.GameChangedEvent;
import com.chessapp.game.service.GameQueryService;
import com.chessapp.game.service.GameCommandService;
import com.chessapp.notification.config.WebSocketProperties;
import com.chessapp.notification.dto.PlayerUpdate;
import com.chessapp.player.dto.PlayerResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/31/26
 * No class-level @Transactional: these tests observe real commits/rollbacks.
 * HTTP login, session storage, messaging, broker and socket endpoint are real.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.servlet.session.cookie.name=CHESS_SESSION",
                "server.servlet.session.cookie.secure=false",
                "spring.session.jdbc.cleanup-cron=-",
                "app.game.clock.timeout-scan-enabled=false"
        }
)
class WebSocketIntegrationTest {

    private static final long WAIT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired private WebSocketProperties properties;
    @Autowired private GameCommandService gameCommandService;
    @Autowired private GameQueryService gameQueryService;
    @Autowired private SimpMessagingTemplate messaging;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired
    private AuthService authService;

    private static final String PASSWORD = "StrongPassword123!";

    private final List<BrowserSession> browsers = new ArrayList<>();

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<Peer> peers = new ArrayList<>();
    private final Set<String> readinessProbes =
            ConcurrentHashMap.newKeySet();

    private List<PlayerResponse> players;
    private GameResponse game;

    @BeforeEach
    void setUp() {
        players = transaction().execute(status -> List.of(
                createPlayer(),
                createPlayer(),
                createPlayer()
        ));

        assertNotNull(players);

        // Commit before connecting: these tests observe later changes,
        // rather than depending on receipt of the creation notification.
        game = gameCommandService.createGame(white(), black());
    }

    @AfterEach
    void cleanUp() {
        try {
            for (Peer peer : peers) {
                try {
                    if (!peer.rejectionObserved
                            && peer.session != null
                            && peer.session.isConnected()) {
                        peer.session.disconnect();
                    }
                } finally {
                    peer.client.stop();
                }
            }
        } finally {
            for (BrowserSession browser : browsers) {
                browser.client().close();
            }
            if (players != null) {
                Object[] ids = players.stream()
                        .map(PlayerResponse::id)
                        .toArray();

                transaction().executeWithoutResult(status -> {
                    jdbc.update("""
                            DELETE FROM matchmaking_queue
                            WHERE player_id IN (?, ?, ?)
                            """, ids);

                    jdbc.update("""
                            DELETE FROM active_game_players
                            WHERE player_id IN (?, ?, ?)
                            """, ids);

                    jdbc.update("""
                            DELETE FROM game_moves
                            WHERE game_id IN (
                                SELECT id FROM games
                                WHERE white_player_id IN (?, ?, ?)
                            )
                            """, ids);

                    jdbc.update("""
                        DELETE FROM player_statistics_processed_games
                        WHERE game_id IN (
                            SELECT id
                            FROM games
                            WHERE white_player_id IN (?, ?, ?)
                        )
                        """, ids);
                    // All games created here use only these fixture players.
                    jdbc.update("""
                            DELETE FROM games
                            WHERE white_player_id IN (?, ?, ?)
                            """, ids);

                    jdbc.update("""
                        DELETE FROM player_statistics
                        WHERE player_id IN (?, ?, ?)
                        """, ids);
                    jdbc.update("""
                        DELETE FROM SPRING_SESSION
                        WHERE PRINCIPAL_NAME IN (?, ?, ?)
                        """,
                        white().toString(),
                        black().toString(),
                        outsider().toString()
                    );

                    jdbc.update("""
                        DELETE FROM auth_credentials
                        WHERE player_id IN (?, ?, ?)
                        """, ids);

                    jdbc.update("""
                        DELETE FROM players
                        WHERE id IN (?, ?, ?)
                        """, ids);
                });
            }
        }
    }

    @Test
    void handshakeRequiresAuthenticatedHttpSession() throws Exception {
        assertHandshakeRejected(
                null,
                properties.allowedOrigin(),
                401
        );
    }

    @Test
    void handshakeRejectsDisallowedOrigin() throws Exception {
        BrowserSession browser = loginBrowser(white());

        assertHandshakeRejected(
                browser,
                "https://untrusted.example",
                403
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "incorrect-csrf"})
    void connectRequiresCsrfAfterAuthenticatedHandshake(
            String csrfToken
    ) throws Exception {
        BrowserSession browser = loginBrowser(white());
        Peer peer = newPeer();

        // This connection is expected to be rejected. Skip graceful
        // DISCONNECT during cleanup, while still stopping the client.
        peer.rejectionObserved = true;

        CompletableFuture<StompSession> connection =
                connect(peer, browser, csrfToken);

        assertThrows(
                ExecutionException.class,
                () -> connection.get(WAIT_SECONDS, TimeUnit.SECONDS)
        );

        assertNotNull(
                peer.protocolErrors.poll(WAIT_SECONDS, TimeUnit.SECONDS),
                "Expected a STOMP ERROR for rejected CONNECT"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "BROKER",
            "OTHER_PLAYER",
            "WILDCARD",
            "APPLICATION"
    })
    void rejectsForbiddenSubscriptions(String kind) throws Exception {
        Peer peer = connectReady(white());

        String forbidden = switch (kind) {
            case "BROKER" -> properties.updatesDestination();
            case "OTHER_PLAYER" -> properties.userPrefix()
                    + "/" + black()
                    + properties.updatesDestination();
            case "WILDCARD" -> properties.userPrefix()
                    + properties.brokerPrefix() + "/**";
            case "APPLICATION" -> properties.applicationPrefix()
                    + properties.updatesPath();
            default -> throw new IllegalArgumentException(kind);
        };

        peer.session.subscribe(forbidden, updateHandler(peer));

        assertNotNull(
                peer.protocolErrors.poll(WAIT_SECONDS, TimeUnit.SECONDS),
                "Expected a STOMP ERROR for subscription to " + forbidden
        );

        peer.rejectionObserved = true;
    }

    @Test
    void rejectsClientPublishing() throws Exception {
        Peer sender = connectReady(white());
        Peer recipient = connectReady(black());

        sender.session.send(
                properties.updatesDestination(),
                "{}".getBytes(StandardCharsets.UTF_8)
        );

        assertNotNull(
                sender.protocolErrors.poll(WAIT_SECONDS, TimeUnit.SECONDS),
                "Expected a STOMP ERROR for client SEND"
        );

        assertNoUpdate(recipient);
        sender.rejectionObserved = true;
    }

    @Test
    void resignationReachesOnlyParticipantsAfterOuterCommit()
            throws Exception {
        Peer whitePeer = connectReady(white());
        Peer blackPeer = connectReady(black());
        Peer outsiderPeer = connectReady(outsider());

        GameResponse finished = transaction().execute(status -> {
            var response = gameCommandService.resign(
                    game.id(),
                    white(),
                    game.version()
            );

            // The service has flushed, but this enclosing transaction
            // has not committed. No successful-change hint may arrive.
            assertNoUpdate(whitePeer);
            assertNoUpdate(blackPeer);
            assertNoUpdate(outsiderPeer);

            return response;
        });

        assertNotNull(finished);

        var expected = new PlayerUpdate(
                GameChangedEvent.Type.GAME_UPDATED.name(),
                game.id(),
                finished.version()
        );

        assertUpdate(whitePeer, expected);
        assertUpdate(blackPeer, expected);
        assertNoUpdate(outsiderPeer);

        var stored = gameQueryService.getGame(game.id(), white());
        assertEquals(GameStatus.FINISHED, stored.status());
        assertEquals(finished.version(), stored.version());
    }

    @Test
    void rolledBackResignationSendsNoUpdate() throws Exception {
        Peer whitePeer = connectReady(white());
        Peer blackPeer = connectReady(black());
        Peer outsiderPeer = connectReady(outsider());

        transaction().executeWithoutResult(status -> {
            gameCommandService.resign(
                    game.id(),
                    white(),
                    game.version()
            );

            assertNoUpdate(whitePeer);
            assertNoUpdate(blackPeer);

            status.setRollbackOnly();
        });

        assertNoUpdate(whitePeer);
        assertNoUpdate(blackPeer);
        assertNoUpdate(outsiderPeer);

        var stored = gameQueryService.getGame(game.id(), white());

        assertEquals(GameStatus.IN_PROGRESS, stored.status());
        assertEquals(game.version(), stored.version());
        assertNull(stored.result());
        assertNull(stored.finishedAt());

        assertEquals(
                Long.valueOf(2),
                jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM active_game_players
                        WHERE game_id = ?
                        """,
                        Long.class,
                        game.id()
                )
        );
    }

    @Test
    void logoutStopsDeliveryToBothTabsSharingTheSession()
            throws Exception {

        BrowserSession browser = loginBrowser(white());

        Peer firstTab = connectReady(white(), browser);
        Peer secondTab = connectReady(white(), browser);
        Peer blackPeer = connectReady(black());

        assertEquals(
                204,
                browserRequest(
                        browser,
                        "POST",
                        "/api/v1/auth/logout",
                        "{}"
                ).statusCode()
        );

        GameResponse updated = gameCommandService.offerDraw(
                game.id(),
                black(),
                game.version()
        );

        var expected = new PlayerUpdate(
                GameChangedEvent.Type.GAME_UPDATED.name(),
                game.id(),
                updated.version()
        );

        // Positive control: publication and live delivery still work.
        assertUpdate(blackPeer, expected);

        // Both sockets were ready before logout.
        assertNoUpdate(firstTab);
        assertNoUpdate(secondTab);
    }

    @Test
    void sessionExpiryStopsDeliveryWithoutWaitingForCleanup()
            throws Exception {

        Peer whitePeer = connectReady(white());
        Peer blackPeer = connectReady(black());

        assertEquals(
                1,
                jdbc.update("""
                    UPDATE SPRING_SESSION
                    SET LAST_ACCESS_TIME = 0,
                        EXPIRY_TIME = 1
                    WHERE PRINCIPAL_NAME = ?
                    """, white().toString())
        );

        GameResponse updated = gameCommandService.offerDraw(
                game.id(),
                black(),
                game.version()
        );

        assertUpdate(
                blackPeer,
                new PlayerUpdate(
                        GameChangedEvent.Type.GAME_UPDATED.name(),
                        game.id(),
                        updated.version()
                )
        );

        assertNoUpdate(whitePeer);
    }

    private void assertHandshakeRejected(
            BrowserSession browser,
            String origin,
            int expectedStatus
    ) throws Exception {
        // A JDK WebSocket client exposes the actual failed-upgrade status.
        try (var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(WAIT_SECONDS))
                .build()) {

            var builder = client.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(WAIT_SECONDS))
                    .header("Origin", origin);

            if (browser != null) {
                builder.header(
                        "Cookie",
                        browser.cookieHeader(httpUri(properties.endpoint()))
                );
            }

            var connection = builder.buildAsync(
                    URI.create(
                            "ws://localhost:" + port + properties.endpoint()
                    ),
                    new java.net.http.WebSocket.Listener() {
                        @Override
                        public void onOpen(java.net.http.WebSocket socket) {
                            // Avoid leaking a connection if rejection fails.
                            socket.abort();
                        }
                    }
            );

            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> connection.get(WAIT_SECONDS, TimeUnit.SECONDS)
            );

            var rejection = assertInstanceOf(
                    WebSocketHandshakeException.class,
                    failure.getCause()
            );

            assertEquals(
                    expectedStatus,
                    rejection.getResponse().statusCode()
            );
        }
    }

    private Peer connectReady(Long playerId) throws Exception {
        return connectReady(playerId, loginBrowser(playerId));
    }

    private Peer connectReady(
            Long playerId,
            BrowserSession browser
    ) throws Exception {
        Peer peer = newPeer();

        peer.session = connect(
                peer,
                browser,
                browser.csrfToken()
        ).get(WAIT_SECONDS, TimeUnit.SECONDS);

        peer.session.subscribe(
                properties.subscriptionDestination(),
                updateHandler(peer)
        );

        // CONNECTED does not acknowledge subscription registration.
        // A private probe establishes readiness through the actual route.
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);

        while (System.nanoTime() < deadline) {
            messaging.convertAndSendToUser(
                    playerId.toString(),
                    properties.updatesDestination(),
                    peer.probe.getBytes(StandardCharsets.UTF_8)
            );

            if (peer.ready.await(100, TimeUnit.MILLISECONDS)) {
                assertHealthy(peer);
                return peer;
            }

            assertHealthy(peer);
        }

        fail("Private subscription did not become ready for Player "
                + playerId);

        return peer;
    }

    private CompletableFuture<StompSession> connect(
            Peer peer,
            BrowserSession browser,
            String csrfToken
    ) throws IOException {
        var handshakeHeaders = new WebSocketHttpHeaders();

        handshakeHeaders.setOrigin(properties.allowedOrigin());
        handshakeHeaders.set(
                HttpHeaders.COOKIE,
                browser.cookieHeader(httpUri(properties.endpoint()))
        );

        var connectHeaders = new StompHeaders();

        if (csrfToken != null && !csrfToken.isEmpty()) {
            connectHeaders.add("X-XSRF-TOKEN", csrfToken);
        }

        return peer.client.connectAsync(
                "ws://localhost:" + port + properties.endpoint(),
                handshakeHeaders,
                connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return byte[].class;
                    }

                    @Override
                    public void handleFrame(
                            StompHeaders headers,
                            Object payload
                    ) {
                        // Session-level frames handled here are ERROR
                        // frames; ordinary MESSAGEs use updateHandler.
                        peer.protocolErrors.add(
                                headers + " " + text(payload)
                        );
                    }

                    @Override
                    public void handleException(
                            StompSession session,
                            StompCommand command,
                            StompHeaders headers,
                            byte[] payload,
                            Throwable exception
                    ) {
                        peer.failures.add(exception);
                    }

                    @Override
                    public void handleTransportError(
                            StompSession session,
                            Throwable exception
                    ) {
                        peer.failures.add(exception);
                    }
                }
        );
    }

    private StompFrameHandler updateHandler(Peer peer) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(
                    StompHeaders headers,
                    Object payload
            ) {
                String body = text(payload);

                // User routing can deliver a fixture probe to multiple sockets
                // belonging to the same Player.
                if (readinessProbes.contains(body)) {
                    if (peer.probe.equals(body)) {
                        peer.ready.countDown();
                    }

                    return;
                }

                try {
                    peer.updates.add(
                            json.readValue(body, PlayerUpdate.class)
                    );
                } catch (Exception exception) {
                    peer.failures.add(exception);
                }
            }
        };
    }

    private Peer newPeer() {
        var client = new WebSocketStompClient(
                new StandardWebSocketClient()
        );

        // Preserve raw STOMP payload bytes regardless of content type.
        // JSON parsing is handled explicitly by updateHandler().
        client.setMessageConverter(new SimpleMessageConverter());

        // These short tests exercise routing/security, not heartbeat
        // negotiation. No client heartbeat scheduler is needed here.
        client.setDefaultHeartbeat(new long[] {0, 0});
        client.start();

        var peer = new Peer(client);
        readinessProbes.add(peer.probe);
        peers.add(peer);
        return peer;
    }

    private void assertUpdate(Peer peer, PlayerUpdate expected)
            throws InterruptedException {
        PlayerUpdate actual = peer.updates.poll(
                WAIT_SECONDS,
                TimeUnit.SECONDS
        );

        assertHealthy(peer);
        assertEquals(expected, actual, "Expected private game update");
    }

    private void assertNoUpdate(Peer peer) {
        try {
            assertNull(
                    peer.updates.poll(300, TimeUnit.MILLISECONDS),
                    "Unexpected private game update"
            );
            assertHealthy(peer);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(
                    "Interrupted while observing socket delivery",
                    exception
            );
        }
    }

    private static void assertHealthy(Peer peer) {
        assertTrue(
                peer.protocolErrors.isEmpty(),
                () -> "Unexpected STOMP errors: " + peer.protocolErrors
        );
        assertTrue(
                peer.failures.isEmpty(),
                () -> "Unexpected client failures: " + peer.failures
        );
    }

    private static String text(Object payload) {
        // ERROR frames may contain headers without a body.
        if (payload == null) {
            return "";
        }

        return new String(
                (byte[]) payload,
                StandardCharsets.UTF_8
        );
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private PlayerResponse createPlayer() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);

        var registered = authService.register(
                new RegisterRequest(
                        "socket_" + suffix,
                        suffix + "@example.com",
                        PASSWORD
                )
        );

        return new PlayerResponse(
                registered.playerId(),
                registered.username(),
                registered.email()
        );
    }

    private Long white() {
        return players.getFirst().id();
    }

    private Long black() {
        return players.get(1).id();
    }

    private Long outsider() {
        return players.get(2).id();
    }

    private BrowserSession loginBrowser(Long playerId) throws Exception {
        PlayerResponse player = players.stream()
                .filter(value -> value.id().equals(playerId))
                .findFirst()
                .orElseThrow();

        var cookies = new CookieManager(
                null,
                CookiePolicy.ACCEPT_ORIGINAL_SERVER
        );

        var client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(WAIT_SECONDS))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        var browser = new BrowserSession(client, cookies);
        browsers.add(browser);

        assertEquals(
                204,
                browserRequest(
                        browser,
                        "GET",
                        "/api/v1/auth/csrf",
                        null
                ).statusCode()
        );

        assertEquals(
                200,
                browserRequest(
                        browser,
                        "POST",
                        "/api/v1/auth/login",
                        """
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(player.email(), PASSWORD)
                ).statusCode()
        );

        // Successful login clears the preceding CSRF token.
        assertEquals(
                204,
                browserRequest(
                        browser,
                        "GET",
                        "/api/v1/auth/csrf",
                        null
                ).statusCode()
        );

        return browser;
    }

    private HttpResponse<String> browserRequest(
            BrowserSession browser,
            String method,
            String path,
            String body
    ) throws Exception {
        var request = HttpRequest.newBuilder(httpUri(path))
                .timeout(Duration.ofSeconds(WAIT_SECONDS));

        if ("POST".equals(method)) {
            request.header("Content-Type", "application/json");
            request.header("X-XSRF-TOKEN", browser.csrfToken());

            request.POST(HttpRequest.BodyPublishers.ofString(
                    body,
                    StandardCharsets.UTF_8
            ));
        } else {
            request.GET();
        }

        return browser.client().send(
                request.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
    }

    private URI httpUri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private record BrowserSession(
            HttpClient client,
            CookieManager cookies
    ) {
        String csrfToken() {
            return cookies.getCookieStore()
                    .getCookies()
                    .stream()
                    .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                    .filter(cookie -> !cookie.hasExpired())
                    .map(java.net.HttpCookie::getValue)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "The browser has no current CSRF cookie"
                    ));
        }

        String cookieHeader(URI uri) throws java.io.IOException {
            return String.join(
                    "; ",
                    cookies.get(uri, Map.of())
                            .getOrDefault("Cookie", List.of())
            );
        }
    }

    private static final class Peer {
        private final WebSocketStompClient client;
        private boolean rejectionObserved;
        private final String probe = "READY-" + UUID.randomUUID();
        private final CountDownLatch ready = new CountDownLatch(1);

        private final BlockingQueue<PlayerUpdate> updates =
                new LinkedBlockingQueue<>();

        private final BlockingQueue<String> protocolErrors =
                new LinkedBlockingQueue<>();

        private final BlockingQueue<Throwable> failures =
                new LinkedBlockingQueue<>();

        private StompSession session;

        private Peer(WebSocketStompClient client) {
            this.client = client;
        }
    }
}
