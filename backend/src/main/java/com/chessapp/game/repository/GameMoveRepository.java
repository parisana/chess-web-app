package com.chessapp.game.repository;

import com.chessapp.game.entity.GameMove;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
/**
 * @author pari on 10/30/26
 */
public interface GameMoveRepository extends JpaRepository<GameMove, Long> {

    List<GameMove> findAllByGameIdOrderBySequenceNumberAsc(Long gameId);
    /**
     * Reads only the history belonging to the game snapshot already loaded
     * by the service. Later committed moves are excluded.
     */
    @Query("""
        SELECT move
        FROM GameMove move
        WHERE move.gameId = :gameId
          AND move.sequenceNumber <= :lastSequence
        ORDER BY move.sequenceNumber ASC
        """)
    List<GameMove> findHistoryThrough(
            @Param("gameId") Long gameId,
            @Param("lastSequence") long lastSequence
    );
}
