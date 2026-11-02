package com.chessapp.player.dto;

import com.chessapp.player.entity.PlayerStatistics;

/**
 * @author pari on 11/2/26
 */
public record PlayerStatisticsResponse(
        long playerId,
        int rating,
        long wins,
        long draws,
        long losses,
        long gamesPlayed
) {

    public static PlayerStatisticsResponse from(
            PlayerStatistics statistics
    ) {
        return new PlayerStatisticsResponse(
                statistics.getPlayerId(),
                statistics.getRating(),
                statistics.getWins(),
                statistics.getDraws(),
                statistics.getLosses(),
                statistics.getGamesPlayed()
        );
    }
}
