package com.chessapp.game.entity;

import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
/**
 * @author pari on 10/30/26
 * V7 migration introduces the entity
 * One accepted move in the history beginning at a game's baseline.
 * Recorded moves have no application update operations.
 */
@Getter
@Entity
@Table(
        name = "game_moves",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_game_moves_sequence",
                columnNames = {"game_id", "sequence_number"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameMove {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false, updatable = false)
    private Long gameId;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private long sequenceNumber;

    @Column(name = "player_id", nullable = false, updatable = false)
    private Long playerId;

    @Column(
            name = "from_square",
            nullable = false,
            updatable = false,
            length = 2
    )
    private String fromSquare;

    @Column(
            name = "to_square",
            nullable = false,
            updatable = false,
            length = 2
    )
    private String toSquare;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false, length = 10)
    private PieceType promotion;

    @Column(
            name = "resulting_fen",
            nullable = false,
            updatable = false,
            columnDefinition = "text"
    )
    private String resultingFen;

    @Column(name = "played_at", nullable = false, updatable = false)
    private Instant playedAt;

    // Existing rows may have no SAN; all newly recorded moves require it.
    @Column(updatable = false, length = 32)
    private String san;

    public GameMove(
            Long gameId,
            long sequenceNumber,
            Long playerId,
            Move move,
            String resultingFen,
            Instant playedAt,
            String san
    ) {
        requirePositiveId(gameId, "Game ID");
        requirePositiveId(playerId, "Player ID");

        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException(
                    "Sequence number must be positive"
            );
        }

        Objects.requireNonNull(move, "Move is required");
        Objects.requireNonNull(playedAt, "Play time is required");

        if (resultingFen == null || resultingFen.isBlank()) {
            throw new IllegalArgumentException(
                    "Resulting FEN must not be blank"
            );
        }

        if (san == null || san.isBlank() || san.length() > 32) {
            throw new IllegalArgumentException(
                    "SAN must contain between 1 and 32 characters"
            );
        }

        this.gameId = gameId;
        this.sequenceNumber = sequenceNumber;
        this.playerId = playerId;
        this.fromSquare = move.from().toAlgebraic();
        this.toSquare = move.to().toAlgebraic();
        this.promotion = move.promotion().orElse(null);
        this.resultingFen = resultingFen;
        this.playedAt = playedAt;
        this.san = san;
    }

    private static void requirePositiveId(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    name + " must be positive"
            );
        }
    }
}
