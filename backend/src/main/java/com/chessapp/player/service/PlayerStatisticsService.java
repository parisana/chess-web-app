package com.chessapp.player.service;

import com.chessapp.player.dto.PlayerStatisticsResponse;
import com.chessapp.player.entity.PlayerStatistics;
import com.chessapp.player.rating.EloCalculator;
import com.chessapp.player.rating.RatingOutcome;
import com.chessapp.player.repository.PlayerRepository;
import com.chessapp.player.repository.PlayerStatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * @author pari on 11/1/26
 */
@Service
@RequiredArgsConstructor
public class PlayerStatisticsService {

    private final PlayerStatisticsRepository statisticsRepository;
    private final PlayerRepository playerRepository;

    /**
     * Ordinary profile read: it does not acquire statistics update locks.
     * Missing accounts match the existing self-profile authentication policy.
     */
    @Transactional(readOnly = true)
    public Optional<PlayerStatisticsResponse> findByPlayerId(long playerId) {
        if (!playerRepository.existsById(playerId)) {
            return Optional.empty();
        }

        var statistics = statisticsRepository.findById(playerId)
                .orElseThrow(() -> new IllegalStateException(
                        "Statistics missing for Player " + playerId
                ));

        return Optional.of(PlayerStatisticsResponse.from(statistics));
    }

    /**
     * Applies one completed game's outcome in the caller's transaction.
     * The caller supplies trusted, persisted game-completion details.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordFinishedGame(
            long gameId,
            long whitePlayerId,
            long blackPlayerId,
            RatingOutcome whiteOutcome
    ) {
        Objects.requireNonNull(whiteOutcome, "White outcome is required");

        if (gameId <= 0
                || whitePlayerId <= 0
                || blackPlayerId <= 0
                || whitePlayerId == blackPlayerId) {
            throw new IllegalArgumentException(
                    "A finished game requires positive IDs and distinct Players"
            );
        }

        if (!statisticsRepository.claimFinishedGame(gameId)) {
            return;
        }

        long lowerId = Math.min(whitePlayerId, blackPlayerId);
        long higherId = Math.max(whitePlayerId, blackPlayerId);

        // Lock in ascending ID order, regardless of color, to avoid
        // opposite lock acquisition between statistics processors.
        PlayerStatistics lower = lockStatisticsRow(lowerId);
        PlayerStatistics higher = lockStatisticsRow(higherId);

        PlayerStatistics white =
                whitePlayerId == lowerId ? lower : higher;
        PlayerStatistics black =
                blackPlayerId == lowerId ? lower : higher;

        // Capture both pre-game ratings before changing either entity.
        var change = EloCalculator.calculate(
                white.getRating(),
                black.getRating(),
                whiteOutcome
        );

        white.recordResult(
                whiteOutcome,
                change.firstPlayerRating()
        );

        black.recordResult(
                opposite(whiteOutcome),
                change.secondPlayerRating()
        );

        // Managed entities need no save(). Flush surfaces write/version
        // failures before returning; it still does not commit.
        statisticsRepository.flush();
    }

    private PlayerStatistics lockStatisticsRow(long playerId) {
        return statisticsRepository.findByPlayerIdForUpdate(playerId)
                .orElseThrow(() -> new IllegalStateException(
                        "Statistics missing for Player " + playerId
                ));
    }

    private static RatingOutcome opposite(RatingOutcome outcome) {
        return switch (outcome) {
            case WIN -> RatingOutcome.LOSS;
            case DRAW -> RatingOutcome.DRAW;
            case LOSS -> RatingOutcome.WIN;
        };
    }
}
