package com.chessapp.game.repository;

import com.chessapp.game.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * @author pari on 10/22/26
 */
public interface GameRepository extends JpaRepository<Game, Long> {

    /**
     * Returns a bounded batch of expiry candidates.

     * No locks are retained from this read. GameService must reload and
     * recheck each candidate within its completion transaction.
     */
    @Query(value = """
        SELECT g.id
        FROM games g
        WHERE g.status = 'IN_PROGRESS'
          AND g.clock_turn_started_at IS NOT NULL
          AND (
              g.clock_turn_started_at
              + (
                  CASE g.clock_running_color
                      WHEN 'WHITE' THEN g.white_remaining_millis
                      WHEN 'BLACK' THEN g.black_remaining_millis
                  END
                  * INTERVAL '1 millisecond'
              )
          ) <= :now
        ORDER BY
            (
                g.clock_turn_started_at
                + (
                    CASE g.clock_running_color
                        WHEN 'WHITE' THEN g.white_remaining_millis
                        WHEN 'BLACK' THEN g.black_remaining_millis
                    END
                    * INTERVAL '1 millisecond'
                )
            ),
            g.id
        LIMIT :batchSize
        """, nativeQuery = true)
    List<Long> findExpiredClockCandidates(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );

}
