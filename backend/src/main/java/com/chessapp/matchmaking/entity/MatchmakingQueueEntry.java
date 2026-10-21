package com.chessapp.matchmaking.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * @author pari on 10/21/26
 */
@Getter
@Entity
@Table(name = "matchmaking_queue")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchmakingQueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

//    Using a scalar playerId, consistent with AuthCredential, so matchmaking does not depend on the player entity.
    @Column(name = "player_id", nullable = false, unique = true)
    private Long playerId;

    @Column(name = "enqueued_at", nullable = false, updatable = false)
    private Instant enqueuedAt;

    public MatchmakingQueueEntry(Long playerId, Instant enqueuedAt) {
        this.playerId = playerId;
        this.enqueuedAt = enqueuedAt;
    }
}
