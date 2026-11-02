package com.chessapp.player.entity;

import com.chessapp.common.entity.BaseEntity;
import com.chessapp.player.rating.EloCalculator;
import com.chessapp.player.rating.RatingOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * @author pari on 11/1/26
 * Statistics accumulated since the rating feature was introduced.
 * Uses a scalar Player ID rather than a managed Player relationship.
 */
@Getter
@Entity
@Table(name = "player_statistics")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerStatistics extends BaseEntity {

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private Long playerId;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false)
    private long wins;

    @Column(nullable = false)
    private long draws;

    @Column(nullable = false)
    private long losses;

    @Version
    @Column(nullable = false)
    private Long version;

    public PlayerStatistics(long playerId) {
        if (playerId <= 0) {
            throw new IllegalArgumentException(
                    "Player ID must be positive"
            );
        }

        this.playerId = playerId;
        this.rating = EloCalculator.INITIAL_RATING;
    }

    /**
     * Records one outcome using a rating already calculated from
     * both Players' pre-game ratings.

     * Duplicate prevention belongs to the application service,
     * which has the completed game ID.
     */
    public void recordResult(
            RatingOutcome outcome,
            int updatedRating
    ) {
        Objects.requireNonNull(outcome, "Outcome is required");

        // Calculate first so overflow cannot leave a partially changed entity.
        long nextWins = wins;
        long nextDraws = draws;
        long nextLosses = losses;

        switch (outcome) {
            case WIN -> nextWins = Math.incrementExact(wins);
            case DRAW -> nextDraws = Math.incrementExact(draws);
            case LOSS -> nextLosses = Math.incrementExact(losses);
        }

        wins = nextWins;
        draws = nextDraws;
        losses = nextLosses;
        rating = updatedRating;
    }

    /**
     * Derived rather than stored, avoiding another counter to synchronize.
     */
    public long getGamesPlayed() {
        return Math.addExact(
                Math.addExact(wins, draws),
                losses
        );
    }
}
