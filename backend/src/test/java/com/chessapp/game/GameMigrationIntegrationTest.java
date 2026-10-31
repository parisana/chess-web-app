package com.chessapp.game;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/31/26
 */
@SpringBootTest
@ActiveProfiles("test")
class GameMigrationIntegrationTest {

    private static final String INITIAL =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1";

    private static final String AFTER_E4 =
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
                    + " b KQkq e3 0 1";

    @Autowired
    private DataSource dataSource;

    private String schema;
    private Connection connection;
    private String originalSchema;
    private JdbcTemplate isolatedTemplate;

    @BeforeEach
    void createIsolatedSchema() throws SQLException {
        // Generated internally from a fixed prefix and hexadecimal UUID:
        // safe to use as an unquoted SQL identifier.
        schema = "migration_test_"
                + UUID.randomUUID().toString().replace("-", "");

        new JdbcTemplate(dataSource)
                .execute("CREATE SCHEMA " + schema);

        connection = dataSource.getConnection();
        originalSchema = connection.getSchema();
        connection.setSchema(schema);

        // Keep every fixture statement on this schema-bound connection.
        // JdbcTemplate must not close it between operations.
        isolatedTemplate = new JdbcTemplate(
                new SingleConnectionDataSource(connection, true)
        );

        // Constraint failures should roll back only their statement,
        // rather than poison a surrounding transaction.
        assertTrue(connection.getAutoCommit());
    }

    @AfterEach
    void removeIsolatedSchema() throws SQLException {
        try {
            if (connection != null) {
                try {
                    connection.setSchema(originalSchema);
                } finally {
                    connection.close();
                }
            }
        } finally {
            if (schema != null) {
                // Drop only this test's generated schema.
                // Do not use Flyway.clean() on the application datasource.
                new JdbcTemplate(dataSource)
                        .execute("DROP SCHEMA IF EXISTS "
                                + schema + " CASCADE");
            }
        }
    }

    @Test
    void upgradesV6GamesWithLegacyBaselinesAndLifecycleConstraints() {
        migrateTo("6");
        seedPlayers();
        insertActiveGame(AFTER_E4, 7);

        isolatedTemplate.update("""
                INSERT INTO games (
                    id, white_player_id, black_player_id,
                    status, current_fen, version,
                    result, termination_reason, finished_at
                )
                VALUES (
                    102, 1, 2,
                    'FINISHED', ?, 12,
                    'WHITE_WIN', 'CHECKMATE', CURRENT_TIMESTAMP
                )
                """, INITIAL);

        isolatedTemplate.update("""
                INSERT INTO games (
                    id, white_player_id, black_player_id,
                    status, current_fen, version,
                    result, termination_reason, finished_at
                )
                VALUES (
                    103, 1, 2,
                    'FINISHED', ?, 18,
                    'DRAW', 'STALEMATE', CURRENT_TIMESTAMP
                )
                """, INITIAL);

        var oldGames = isolatedTemplate.queryForList("""
                SELECT id, white_player_id, black_player_id,
                       status, current_fen, version,
                       result, termination_reason, finished_at,
                       created_at, updated_at
                FROM games
                ORDER BY id
                """);

        var oldReservations = isolatedTemplate.queryForList("""
                SELECT player_id, game_id
                FROM active_game_players
                ORDER BY player_id
                """);

        migrateTo("10");

        // Compare only columns that existed at V6. Adding metadata
        // must not rewrite the historical position or outcome.
        assertEquals(
                oldGames,
                isolatedTemplate.queryForList("""
                        SELECT id, white_player_id, black_player_id,
                               status, current_fen, version,
                               result, termination_reason, finished_at,
                               created_at, updated_at
                        FROM games
                        ORDER BY id
                        """)
        );

        assertEquals(
                oldReservations,
                isolatedTemplate.queryForList("""
                        SELECT player_id, game_id
                        FROM active_game_players
                        ORDER BY player_id
                        """)
        );

        assertEquals(3L, count("""
                SELECT count(*)
                FROM games
                WHERE history_start_fen = current_fen
                  AND history_complete = FALSE
                  AND recorded_move_count = 0
                  AND draw_offered_by_player_id IS NULL
                """));

        assertEquals(0L, count("SELECT count(*) FROM game_moves"));

        // The outsider exists, so rejection comes from offer ownership,
        // rather than a missing Player fixture.
        assertThrows(
                DataIntegrityViolationException.class,
                () -> isolatedTemplate.update("""
                        UPDATE games
                        SET draw_offered_by_player_id = 3
                        WHERE id = 101
                        """)
        );

        isolatedTemplate.update("""
                UPDATE games
                SET draw_offered_by_player_id = 1
                WHERE id = 101
                """);

        assertEquals(
                Long.valueOf(1),
                isolatedTemplate.queryForObject("""
                        SELECT draw_offered_by_player_id
                        FROM games
                        WHERE id = 101
                        """, Long.class)
        );

        // A finished game cannot retain a pending offer.
        assertThrows(
                DataIntegrityViolationException.class,
                () -> isolatedTemplate.update("""
                        UPDATE games
                        SET status = 'FINISHED',
                            result = 'DRAW',
                            termination_reason = 'DRAW_AGREEMENT',
                            finished_at = CURRENT_TIMESTAMP
                        WHERE id = 101
                        """)
        );

        // Agreement must produce DRAW, even when the offer is cleared.
        assertThrows(
                DataIntegrityViolationException.class,
                () -> isolatedTemplate.update("""
                        UPDATE games
                        SET status = 'FINISHED',
                            result = 'WHITE_WIN',
                            termination_reason = 'DRAW_AGREEMENT',
                            finished_at = CURRENT_TIMESTAMP,
                            draw_offered_by_player_id = NULL
                        WHERE id = 101
                        """)
        );

        assertEquals(1, isolatedTemplate.update("""
                UPDATE games
                SET status = 'FINISHED',
                    result = 'DRAW',
                    termination_reason = 'DRAW_AGREEMENT',
                    finished_at = CURRENT_TIMESTAMP,
                    draw_offered_by_player_id = NULL
                WHERE id = 101
                """));

        assertEquals(1L, count("""
                SELECT count(*)
                FROM games
                WHERE id = 101
                  AND status = 'FINISHED'
                  AND result = 'DRAW'
                  AND termination_reason = 'DRAW_AGREEMENT'
                  AND finished_at IS NOT NULL
                  AND draw_offered_by_player_id IS NULL
                """));
    }

    @Test
    void preservesPreSanHistoryAndV9ResignationWhenUpgradingToV10() {
        migrateTo("7");
        seedPlayers();
        insertActiveGame(AFTER_E4, 7);

        isolatedTemplate.update("""
                UPDATE games
                SET history_start_fen = ?,
                    history_complete = TRUE,
                    recorded_move_count = 1
                WHERE id = 101
                """, INITIAL);

        // SAN does not exist at V7. This is a genuinely pre-SAN row.
        isolatedTemplate.update("""
                INSERT INTO game_moves (
                    id, game_id, sequence_number, player_id,
                    from_square, to_square, promotion,
                    resulting_fen, played_at
                )
                VALUES (
                    201, 101, 1, 1,
                    'e2', 'e4', NULL,
                    ?, CURRENT_TIMESTAMP
                )
                """, AFTER_E4);

        var oldMove = isolatedTemplate.queryForMap("""
                SELECT id, game_id, sequence_number, player_id,
                       from_square, to_square, promotion,
                       resulting_fen, played_at
                FROM game_moves
                WHERE id = 201
                """);

        migrateTo("8");

        assertNull(isolatedTemplate.queryForObject("""
                SELECT san FROM game_moves WHERE id = 201
                """, String.class));

        // V8 still permits only checkmate/stalemate completion.
        assertThrows(
                DataIntegrityViolationException.class,
                this::finishByResignation
        );

        migrateTo("9");
        finishByResignation();

        // Represent the reservation release that the application would
        // perform in the same transaction as successful completion.
        isolatedTemplate.update("""
                DELETE FROM active_game_players WHERE game_id = 101
                """);

        var gameAtV9 = isolatedTemplate.queryForMap("""
                SELECT id, status, current_fen, version,
                       result, termination_reason, finished_at,
                       history_start_fen, history_complete,
                       recorded_move_count, created_at, updated_at
                FROM games
                WHERE id = 101
                """);

        migrateTo("10");

        assertEquals(
                gameAtV9,
                isolatedTemplate.queryForMap("""
                        SELECT id, status, current_fen, version,
                               result, termination_reason, finished_at,
                               history_start_fen, history_complete,
                               recorded_move_count, created_at, updated_at
                        FROM games
                        WHERE id = 101
                        """)
        );

        assertEquals(
                oldMove,
                isolatedTemplate.queryForMap("""
                        SELECT id, game_id, sequence_number, player_id,
                               from_square, to_square, promotion,
                               resulting_fen, played_at
                        FROM game_moves
                        WHERE id = 201
                        """)
        );

        assertNull(isolatedTemplate.queryForObject("""
                SELECT san FROM game_moves WHERE id = 201
                """, String.class));

        assertEquals(0L, count("""
                SELECT count(*) FROM active_game_players
                """));

        assertEquals(1L, count("""
                SELECT count(*)
                FROM games
                WHERE id = 101
                  AND result = 'BLACK_WIN'
                  AND termination_reason = 'RESIGNATION'
                  AND draw_offered_by_player_id IS NULL
                """));

        // All versioned migrations through this checkpoint were applied.
        assertEquals(10L, count("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE success = TRUE
                  AND version IS NOT NULL
                """));
    }

    private void migrateTo(String version) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema(schema)
                .schemas(schema)
                .createSchemas(false)
                .cleanDisabled(true)
                .target(version)
                .load();

        flyway.migrate();
        flyway.validate();

        assertEquals(
                version,
                flyway.info().current().getVersion().getVersion()
        );
    }

    private void seedPlayers() {
        isolatedTemplate.update("""
                INSERT INTO players (id, username, email)
                VALUES
                    (1, 'migration_white', 'white@example.com'),
                    (2, 'migration_black', 'black@example.com'),
                    (3, 'migration_outsider', 'outsider@example.com')
                """);
    }

    private void insertActiveGame(String fen, long version) {
        // Works at V6 and V7: V7 supplies defaults for completeness/count,
        // while its nonnullable baseline must be supplied explicitly.
        boolean hasHistory = count("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = 'games'
                  AND column_name = 'history_start_fen'
                """, schema) == 1;

        if (hasHistory) {
            isolatedTemplate.update("""
                    INSERT INTO games (
                        id, white_player_id, black_player_id,
                        status, current_fen, version, history_start_fen
                    )
                    VALUES (101, 1, 2, 'IN_PROGRESS', ?, ?, ?)
                    """, fen, version, fen);
        } else {
            isolatedTemplate.update("""
                    INSERT INTO games (
                        id, white_player_id, black_player_id,
                        status, current_fen, version
                    )
                    VALUES (101, 1, 2, 'IN_PROGRESS', ?, ?)
                    """, fen, version);
        }

        isolatedTemplate.update("""
                INSERT INTO active_game_players (player_id, game_id)
                VALUES (1, 101), (2, 101)
                """);
    }

    private void finishByResignation() {
        isolatedTemplate.update("""
                UPDATE games
                SET status = 'FINISHED',
                    result = 'BLACK_WIN',
                    termination_reason = 'RESIGNATION',
                    finished_at = CURRENT_TIMESTAMP
                WHERE id = 101
                """);
    }

    private long count(String sql, Object... arguments) {
        Long result = isolatedTemplate.queryForObject(
                sql,
                Long.class,
                arguments
        );

        assertNotNull(result);
        return result;
    }
}
