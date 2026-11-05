package com.chessapp.game.entity;

import com.chessapp.common.entity.BaseEntity;
import com.chessapp.game.clock.GameClockCalculator;
import com.chessapp.game.clock.GameClockState;
import com.chessapp.game.rules.model.PieceColor;
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

    //// game history related fields ////
    @Column(
            name = "history_start_fen",
            nullable = false,
            updatable = false,
            columnDefinition = "text"
    )
    private String historyStartFen;

    @Column(
            name = "history_complete",
            nullable = false,
            updatable = false
    )

    // True when every move since this game's creation is recorded.
    // Existing games migrated with only their current FEN remain false.
    private boolean historyComplete;

    @Column(name = "recorded_move_count", nullable = false)
    private long recordedMoveCount;

    @Column(name = "draw_offered_by_player_id")
    private Long drawOfferedByPlayerId;

    // Null balances identify an untimed game.
// Timed games retain both balances after completion.
    @Column(name = "white_remaining_millis")
    private Long whiteRemainingMillis;

    @Column(name = "black_remaining_millis")
    private Long blackRemainingMillis;

    @Enumerated(EnumType.STRING)
    @Column(name = "clock_running_color", length = 5)
    private PieceColor clockRunningColor;

    @Column(name = "clock_turn_started_at")
    private Instant clockTurnStartedAt;

    // Immutable display details captured by the normal game-creation path.
    // Null values identify games created before snapshots were introduced.
    @Column(name = "white_username_at_start", length = 30, updatable = false)
    private String whiteUsernameAtStart;

    @Column(name = "black_username_at_start", length = 30, updatable = false)
    private String blackUsernameAtStart;

    @Column(name = "white_rating_at_start", updatable = false)
    private Integer whiteRatingAtStart;

    @Column(name = "black_rating_at_start", updatable = false)
    private Integer blackRatingAtStart;

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
        // New games retain their starting position before any moves occur.
        // Migration V7 separately establishes baselines for existing games.
        this.historyStartFen = initialFen;
        this.historyComplete = true;
        this.recordedMoveCount = 0;
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
        // Application policy: any accepted move ends the pending offer.
        // Rejected proposals never reach this mutation.
        drawOfferedByPlayerId = null;
    }

    /**
     * Advances the sequence within this game's recorded history.
     * The service must persist the corresponding move in the same transaction.
     */
    public long advanceRecordedMoveCount() {
        requireInProgress();

        recordedMoveCount = Math.incrementExact(recordedMoveCount);
        return recordedMoveCount;
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
        // Validate the outcome before freezing or changing completion fields.
        freezeClock(finishedAt);

        this.result = result;
        this.terminationReason = reason;
        this.finishedAt = finishedAt;
        this.status = GameStatus.FINISHED;
        this.drawOfferedByPlayerId = null;
    }

    /**
     * Records one pending offer without changing the position or move count.
     * The service owns authorization and application-specific rejection errors.
     */
    public void offerDraw(Long offeringPlayerId) {
        requireInProgress();

        boolean isParticipant =
                whitePlayerId.equals(offeringPlayerId)
                        || blackPlayerId.equals(offeringPlayerId);

        if (!isParticipant) {
            throw new IllegalArgumentException(
                    "A draw offer requires a participating Player"
            );
        }

        if (drawOfferedByPlayerId != null) {
            throw new IllegalStateException(
                    "A draw offer is already pending"
            );
        }

        drawOfferedByPlayerId = offeringPlayerId;
    }

    /**
     * Clears pending negotiation while leaving the game active.
     * The service validates who may decline or withdraw the offer.
     */
    public void clearDrawOffer() {
        requireInProgress();
        drawOfferedByPlayerId = null;
    }

    /**
     * Captures participant details once, before the game is persisted.
     * Later profile edits and rating changes must not rewrite the snapshot.
     */
    public void captureParticipantDetails(
            String whiteUsername,
            String blackUsername,
            int whiteRating,
            int blackRating
    ) {
        requireInProgress();

        if (id != null || whiteUsernameAtStart != null) {
            throw new IllegalStateException(
                    "Participant details have already been established"
            );
        }

        requireSnapshotUsername(whiteUsername);
        requireSnapshotUsername(blackUsername);

        whiteUsernameAtStart = whiteUsername;
        blackUsernameAtStart = blackUsername;
        whiteRatingAtStart = whiteRating;
        blackRatingAtStart = blackRating;
    }

    private static void requireSnapshotUsername(String username) {
        if (username == null || username.isBlank() || username.length() > 30) {
            throw new IllegalArgumentException(
                    "A snapshot username must contain 1–30 characters"
            );
        }
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
            case CHECKMATE, RESIGNATION, TIMEOUT -> {
                if (result == GameResult.DRAW) {
                    throw new IllegalArgumentException(
                            "Checkmate, resignation or timeout requires a winner"
                    );
                }
            }
            case STALEMATE, DRAW_AGREEMENT -> {
                if (result != GameResult.DRAW) {
                    throw new IllegalArgumentException(
                            "Stalemate or draw agreement requires a draw"
                    );
                }
            }
        }
    }

    /**
     * Whether this game has clock balances, including frozen final balances.
     */
    public boolean hasClock() {
        return whiteRemainingMillis != null;
    }

    /**
     * Starts White's clock for a newly created standard game.
     *
     * The service supplies configuration and server time. Existing persisted
     * games are not automatically converted into timed games.
     */
    public void startClock(
            long initialTimeMillis,
            Instant startedAt
    ) {
        requireInProgress();

        if (hasClock()) {
            throw new IllegalStateException(
                    "A game clock can only be initialized once"
            );
        }

        if (recordedMoveCount != 0) {
            throw new IllegalStateException(
                    "A game clock must start before any recorded moves"
            );
        }

        // Construct and validate before changing managed state.
        var initial = GameClockCalculator.start(
                initialTimeMillis,
                startedAt
        );

        applyRunningClock(initial);
    }

    /**
     * Returns an immutable snapshot of the active clock.

     * Finished games expose their frozen balances through the ordinary
     * balance getters; they do not have a running clock snapshot.
     */
    public GameClockState runningClockState() {
        requireInProgress();

        if (!hasClock()) {
            throw new IllegalStateException(
                    "Untimed games do not have a running clock"
            );
        }

        return new GameClockState(
                whiteRemainingMillis,
                blackRemainingMillis,
                clockRunningColor,
                clockTurnStartedAt
        );
    }

    /**
     * Accounts for an accepted move and starts the opponent's clock.

     * The service owns move legality and transaction ordering. Untimed games
     * intentionally require no clock mutation.
     */
    public void advanceClockAfterAcceptedMove(Instant acceptedAt) {
        requireInProgress();
        Objects.requireNonNull(
                acceptedAt,
                "Move acceptance time is required"
        );

        if (!hasClock()) {
            return;
        }

        // after a move, colors are switched and clockTurnStartedAt is set to acceptedAt for the next mover. [creating a frozen clock effect for this mover]
        var next = GameClockCalculator.afterAcceptedMove(
                runningClockState(),
                acceptedAt
        );

        applyRunningClock(next);
    }

    private void applyRunningClock(GameClockState state) {
        whiteRemainingMillis = state.whiteRemainingMillis();
        blackRemainingMillis = state.blackRemainingMillis();
        clockRunningColor = state.runningColor();
        clockTurnStartedAt = state.turnStartedAt();
    }

    /**
     * Deducts elapsed time once and removes the running-turn metadata.

     * Calculate both balances before assigning either, so validation failures
     * cannot leave partially changed clock fields.
     */
    private void freezeClock(Instant finishedAt) {
        if (!hasClock()) {
            return;
        }

        var state = runningClockState();

        long finalWhite = GameClockCalculator.remainingMillis(
                state,
                PieceColor.WHITE,
                finishedAt
        );
        long finalBlack = GameClockCalculator.remainingMillis(
                state,
                PieceColor.BLACK,
                finishedAt
        );

        whiteRemainingMillis = finalWhite;
        blackRemainingMillis = finalBlack;
        clockRunningColor = null;
        clockTurnStartedAt = null;
    }
}
