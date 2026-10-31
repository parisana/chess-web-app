package com.chessapp.notification;

import com.chessapp.game.dto.GameResponse;
import com.chessapp.game.entity.GameStatus;
import com.chessapp.game.event.GameChangedEvent;
import com.chessapp.game.service.GameService;
import com.chessapp.notification.config.WebSocketProperties;
import com.chessapp.notification.dto.PlayerUpdate;
import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.service.PlayerService;
import com.chessapp.security.service.JwtTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/31/26
 * There is deliberately no class-level @Transactional: the tests need real commits and rollbacks.
 * The messaging template, broker, JWT decoder and socket endpoint are all real.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class WebSocketIntegrationTest {

    private static final long WAIT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired private WebSocketProperties properties;
    @Autowired private JwtTokenService tokenService;
    @Autowired private PlayerService playerService;
    @Autowired private GameService gameService;
    @Autowired private SimpMessagingTemplate messaging;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<Peer> peers = new ArrayList<>();

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
        game = gameService.createGame(white(), black());
    }

    @AfterEach
    void cleanUp() {
        try {
            for (Peer peer : peers) {
                try {
                    if (peer.session != null && peer.session.isConnected()) {
                        peer.session.disconnect();
                    }
                } finally {
                    peer.client.stop();
                }
            }
        } finally {
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
                            DELETE FROM games
                            WHERE white_player_id IN (?, ?, ?)
                            """, ids);

                    jdbc.update("""
                            DELETE FROM players
                            WHERE id IN (?, ?, ?)
                            """, ids);
                });
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-jwt"})
    void connectRequiresValidBearerCredentials(String token)
            throws Exception {
        Peer peer = newPeer();

        CompletableFuture<StompSession> connection = connect(peer, token);

        // A timeout does not count as successful rejection.
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
    }

    @Test
    void resignationReachesOnlyParticipantsAfterOuterCommit()
            throws Exception {
        Peer whitePeer = connectReady(white());
        Peer blackPeer = connectReady(black());
        Peer outsiderPeer = connectReady(outsider());

        GameResponse finished = transaction().execute(status -> {
            var response = gameService.resign(
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

        var stored = gameService.getGame(game.id(), white());
        assertEquals(GameStatus.FINISHED, stored.status());
        assertEquals(finished.version(), stored.version());
    }

    @Test
    void rolledBackResignationSendsNoUpdate() throws Exception {
        Peer whitePeer = connectReady(white());
        Peer blackPeer = connectReady(black());
        Peer outsiderPeer = connectReady(outsider());

        transaction().executeWithoutResult(status -> {
            gameService.resign(
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

        var stored = gameService.getGame(game.id(), white());

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

    private Peer connectReady(Long playerId) throws Exception {
        Peer peer = newPeer();

        String token = tokenService.issueAccessToken(playerId)
                .getTokenValue();

        peer.session = connect(peer, token)
                .get(WAIT_SECONDS, TimeUnit.SECONDS);

        peer.session.subscribe(
                properties.subscriptionDestination(),
                updateHandler(peer)
        );

        // CONNECTED does not acknowledge subscription registration.
        // Repeated private probes establish that this specific client
        // can receive through the actual user-destination route.
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
            String token
    ) {
        var handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin(properties.allowedOrigin());

        var connectHeaders = new StompHeaders();

        if (!token.isEmpty()) {
            connectHeaders.add("Authorization", "Bearer " + token);
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

                // Late replies to repeated probes remain fixture traffic and must never pollute application assertions.
                if (peer.probe.equals(body)) {
                    peer.ready.countDown();
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

        return playerService.createPlayer(
                "socket_" + suffix,
                suffix + "@example.com"
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

    private static final class Peer {

        private final WebSocketStompClient client;
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
