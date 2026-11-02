package com.chessapp.player.repository;

import com.chessapp.player.entity.PlayerStatistics;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
/**
 * @author pari on 11/1/26
 * When processing a game, we’ll lock the lower Player ID first, then the higher ID, regardless of color.
 * Consistent ordering prevents opposite lock acquisition between rating updates.
 */
public interface PlayerStatisticsRepository
        extends JpaRepository<PlayerStatistics, Long> {

    /**
     * Reads and locks this Player's statistics until the transaction ends.
     * Must be called within an existing write transaction.

     * Another transaction calling this method for the same Player must wait,
     * even if it only intends to read. Ordinary non-locking reads can still
     * access the previously committed data.

     * When locking both participants, acquire locks in ascending Player ID
     * order, regardless of color, to avoid opposite lock acquisition.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select statistics
            from PlayerStatistics statistics
            where statistics.playerId = :playerId
            """)
    Optional<PlayerStatistics> findByPlayerIdForUpdate(
            @Param("playerId") long playerId
    );

    @Modifying
    @Query(value = """
        INSERT INTO player_statistics_processed_games (
            game_id,
            statistics_applied
        )
        VALUES (:gameId, TRUE)
        ON CONFLICT (game_id) DO NOTHING
        """, nativeQuery = true)
    int insertFinishedGameClaim(@Param("gameId") long gameId);

    /**
     * Returns true when claimed, or false when already processed/excluded.
     * Must run within the caller's statistics transaction.
     */
    default boolean claimFinishedGame(long gameId) {
        return insertFinishedGameClaim(gameId) == 1;
    }
}
