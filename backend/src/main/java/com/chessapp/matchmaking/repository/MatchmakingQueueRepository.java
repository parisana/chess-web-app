package com.chessapp.matchmaking.repository;


import com.chessapp.matchmaking.entity.MatchmakingQueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * @author pari on 10/21/26
 *
 * | Operation          | Result |
 * |--------------------|---|
 * | First join         | Create a queue entry |
 * | Repeated join      | Preserve the existing entry and enqueue time |
 * | Leave              | Remove the entry |
 * | Repeated leave     | Succeed without changing anything |
 * | Status             | Return `WAITING` or `NOT_QUEUED` |
 */
public interface MatchmakingQueueRepository
        extends JpaRepository<MatchmakingQueueEntry, Long> {

    Optional<MatchmakingQueueEntry> findByPlayerId(Long playerId);

    /*
    *   Why perform that apparently redundant update? It keeps the existing row locked until our transaction completes, allowing the following lookup to return the entry without a concurrent leave removing it between those statements.
        This has a cost: repeated joins perform database updates even though the business data stays the same. That is an explicit tradeoff for this small implementation.
    * */
    @Modifying
    @Query(value = """
            INSERT INTO matchmaking_queue (player_id, enqueued_at)
            VALUES (:playerId, :enqueuedAt)
            ON CONFLICT (player_id)
            DO UPDATE SET player_id = EXCLUDED.player_id
            """, nativeQuery = true)
    int insertOrKeepExisting(
            @Param("playerId") Long playerId,
            @Param("enqueuedAt") Instant enqueuedAt
    );

    @Modifying
    @Query("""
            DELETE FROM MatchmakingQueueEntry entry
            WHERE entry.playerId = :playerId
            """)
    int deleteEntryByPlayerId(@Param("playerId") Long playerId);
}
