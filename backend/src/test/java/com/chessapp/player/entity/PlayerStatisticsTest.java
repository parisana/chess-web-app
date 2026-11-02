package com.chessapp.player.entity;

import com.chessapp.player.rating.EloCalculator;
import com.chessapp.player.rating.RatingOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author pari on 11/1/26
 */
class PlayerStatisticsTest {

    @Test
    void newPlayerStartsWithInitialRatingAndNoGames() {
        PlayerStatistics statistics = new PlayerStatistics(7L);

        assertAll(
                () -> assertEquals(
                        7L,
                        statistics.getPlayerId().longValue()
                ),
                () -> assertEquals(
                        EloCalculator.INITIAL_RATING,
                        statistics.getRating()
                ),
                () -> assertEquals(0L, statistics.getWins()),
                () -> assertEquals(0L, statistics.getDraws()),
                () -> assertEquals(0L, statistics.getLosses()),
                () -> assertEquals(0L, statistics.getGamesPlayed())
        );
    }

    @ParameterizedTest
    @CsvSource({
            "WIN,  1216, 1, 0, 0",
            "DRAW, 1200, 0, 1, 0",
            "LOSS, 1184, 0, 0, 1"
    })
    void resultUpdatesOnlyItsMatchingCounter(
            RatingOutcome outcome,
            int updatedRating,
            long expectedWins,
            long expectedDraws,
            long expectedLosses
    ) {
        PlayerStatistics statistics = new PlayerStatistics(7L);

        statistics.recordResult(outcome, updatedRating);

        assertAll(
                () -> assertEquals(
                        updatedRating,
                        statistics.getRating()
                ),
                () -> assertEquals(
                        expectedWins,
                        statistics.getWins()
                ),
                () -> assertEquals(
                        expectedDraws,
                        statistics.getDraws()
                ),
                () -> assertEquals(
                        expectedLosses,
                        statistics.getLosses()
                ),
                () -> assertEquals(
                        1L,
                        statistics.getGamesPlayed()
                )
        );
    }

    @Test
    void successiveResultsAccumulateWithoutResettingOtherCounters() {
        PlayerStatistics statistics = new PlayerStatistics(7L);

        statistics.recordResult(RatingOutcome.WIN, 1216);
        statistics.recordResult(RatingOutcome.DRAW, 1215);
        statistics.recordResult(RatingOutcome.LOSS, 1198);
        statistics.recordResult(RatingOutcome.WIN, 1214);

        assertAll(
                () -> assertEquals(2L, statistics.getWins()),
                () -> assertEquals(1L, statistics.getDraws()),
                () -> assertEquals(1L, statistics.getLosses()),
                () -> assertEquals(4L, statistics.getGamesPlayed()),
                () -> assertEquals(1214, statistics.getRating())
        );
    }

    @Test
    void missingOutcomePreservesExistingStatistics() {
        PlayerStatistics statistics = new PlayerStatistics(7L);
        statistics.recordResult(RatingOutcome.WIN, 1216);

        assertThrows(
                NullPointerException.class,
                () -> statistics.recordResult(null, 900)
        );

        assertAll(
                () -> assertEquals(1216, statistics.getRating()),
                () -> assertEquals(1L, statistics.getWins()),
                () -> assertEquals(0L, statistics.getDraws()),
                () -> assertEquals(0L, statistics.getLosses()),
                () -> assertEquals(1L, statistics.getGamesPlayed())
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void nonpositivePlayerIdIsRejected(long playerId) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerStatistics(playerId)
        );
    }
}