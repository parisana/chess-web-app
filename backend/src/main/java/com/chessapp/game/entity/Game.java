package com.chessapp.game.entity;

import com.chessapp.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    private static void requireValidPlayerId(Long playerId) {
        if (playerId == null || playerId <= 0) {
            throw new IllegalArgumentException(
                    "Player ID must be positive"
            );
        }
    }
}
