package com.chessapp.matchmaking.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * @author pari on 10/22/26
 */
@Repository
@RequiredArgsConstructor
public class MatchmakingLockRepository {

    private final JdbcTemplate jdbcTemplate;

    public void lockQueue() {
        // Application-defined namespace and queue identifier.
        jdbcTemplate.execute(
                "SELECT pg_advisory_xact_lock(1128813395, 1)"
        );
    }
}
