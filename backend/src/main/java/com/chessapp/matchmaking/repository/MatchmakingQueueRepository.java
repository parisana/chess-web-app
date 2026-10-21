package com.chessapp.matchmaking.repository;


import com.chessapp.matchmaking.entity.MatchmakingQueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * @author pari on 10/21/26
 */
public interface MatchmakingQueueRepository
        extends JpaRepository<MatchmakingQueueEntry, Long> {

    Optional<MatchmakingQueueEntry> findByPlayerId(Long playerId);
}
