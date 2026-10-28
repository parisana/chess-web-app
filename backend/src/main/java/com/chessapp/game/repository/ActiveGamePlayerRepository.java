package com.chessapp.game.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * @author pari on 10/22/26
 */
@Repository
@RequiredArgsConstructor
public class ActiveGamePlayerRepository {

    // active_game_players table has no entity behavior or callbacks, so a small JDBC repository is sufficient.
    private final JdbcTemplate jdbcTemplate;

    public int reservePlayers(
            Long gameId,
            Long whitePlayerId,
            Long blackPlayerId
    ) {
        long firstPlayerId = Math.min(whitePlayerId, blackPlayerId);
        long secondPlayerId = Math.max(whitePlayerId, blackPlayerId);

        // Both reservations are attempted in ascending player-ID order. Consistent ordering reduces deadlock risk when requests overlap.
        return jdbcTemplate.update("""
                INSERT INTO active_game_players (player_id, game_id)
                VALUES (?, ?), (?, ?)
                ON CONFLICT (player_id) DO NOTHING
                """,
                firstPlayerId, gameId,
                secondPlayerId, gameId
        );
    }

    public int releasePlayers(Long gameId) {
        // Scope by game so an old command cannot release a later game's players.
        return jdbcTemplate.update(
                "DELETE FROM active_game_players WHERE game_id = ?",
                gameId
        );
    }

    public Optional<Long> findGameIdByPlayerId(Long playerId) {
        return jdbcTemplate.query("""
                SELECT game_id
                FROM active_game_players
                WHERE player_id = ?
                """,
                (resultSet, rowNumber) -> resultSet.getLong("game_id"),
                playerId
        ).stream().findFirst();
    }
}
