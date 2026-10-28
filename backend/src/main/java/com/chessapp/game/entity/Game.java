package com.chessapp.game.entity;

import com.chessapp.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * @author pari on 10/22/26
 */
@Getter
@Entity
@Table(name = "games")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Game extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "white_player_id", nullable = false, updatable = false)
    private Long whitePlayerId;

    @Column(name = "black_player_id", nullable = false, updatable = false)
    private Long blackPlayerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GameStatus status;

    @Column(name = "current_fen", nullable = false, columnDefinition = "text")
    private String currentFen;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private GameResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "termination_reason", length = 30)
    private GameTerminationReason terminationReason;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public Game(
            Long whitePlayerId,
            Long blackPlayerId,
            String initialFen
    ) {
        requireValidPlayerId(whitePlayerId);
        requireValidPlayerId(blackPlayerId);

        if (whitePlayerId.equals(blackPlayerId)) {
            throw new IllegalArgumentException(
                    "A game requires two distinct players"
            );
        }

        if (initialFen == null || initialFen.isBlank()) {
            throw new IllegalArgumentException(
                    "Initial FEN must not be blank"
            );
        }

        this.whitePlayerId = whitePlayerId;
        this.blackPlayerId = blackPlayerId;
        this.status = GameStatus.IN_PROGRESS;
        this.currentFen = initialFen;
    }

    /**
     * Updates placement and metadata already validated by the rules boundary.
     */
    public void updatePosition(String fen) {
        requireInProgress();

        if (fen == null || fen.isBlank()) {
            throw new IllegalArgumentException(
                    "Current FEN must not be blank"
            );
        }

        currentFen = fen;
    }

    /**
     * Records a supported completion without evaluating chess rules here.
     */
    public void finish(
            GameResult result,
            GameTerminationReason reason,
            Instant finishedAt
    ) {
        requireInProgress();

        Objects.requireNonNull(result, "Game result is required");
        Objects.requireNonNull(reason, "Termination reason is required");
        Objects.requireNonNull(finishedAt, "Finish time is required");

        validateResult(result, reason);

        // Validate everything before changing any completion field.
        this.result = result;
        this.terminationReason = reason;
        this.finishedAt = finishedAt;
        this.status = GameStatus.FINISHED;
    }

    private void requireInProgress() {
        if (status != GameStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Finished games cannot change"
            );
        }
    }

    private static void requireValidPlayerId(Long playerId) {
        if (playerId == null || playerId <= 0) {
            throw new IllegalArgumentException(
                    "Player ID must be positive"
            );
        }
    }

    private static void validateResult(
            GameResult result,
            GameTerminationReason reason
    ) {
        switch (reason) {
            case CHECKMATE -> {
                if (result == GameResult.DRAW) {
                    throw new IllegalArgumentException(
                            "Checkmate requires a winner"
                    );
                }
            }
            case STALEMATE -> {
                if (result != GameResult.DRAW) {
                    throw new IllegalArgumentException(
                            "Stalemate requires a draw"
                    );
                }
            }
        }
    }
}
