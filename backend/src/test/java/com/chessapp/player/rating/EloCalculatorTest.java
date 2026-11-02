package com.chessapp.player.rating;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author pari on 11/1/26
 */
class EloCalculatorTest {

    @ParameterizedTest
    @CsvSource({
            "WIN,  1216, 1184,  16",
            "DRAW, 1200, 1200,   0",
            "LOSS, 1184, 1216, -16"
    })
    void equalRatingsUseSymmetricAdjustments(
            RatingOutcome outcome,
            int expectedFirst,
            int expectedSecond,
            int expectedDelta
    ) {
        RatingChange change = EloCalculator.calculate(
                1200,
                1200,
                outcome
        );

        assertEquals(expectedFirst, change.firstPlayerRating());
        assertEquals(expectedSecond, change.secondPlayerRating());
        assertEquals(expectedDelta, change.firstPlayerDelta());
    }

    @ParameterizedTest
    @CsvSource({
            "WIN,  1229, 1571, 29",
            "DRAW, 1213, 1587, 13",
            "LOSS, 1197, 1603, -3"
    })
    void lowerRatedPlayerGainsMoreForAnUnexpectedResult(
            RatingOutcome outcome,
            int expectedFirst,
            int expectedSecond,
            int expectedDelta
    ) {
        RatingChange change = EloCalculator.calculate(
                1200,
                1600,
                outcome
        );

        assertEquals(expectedFirst, change.firstPlayerRating());
        assertEquals(expectedSecond, change.secondPlayerRating());
        assertEquals(expectedDelta, change.firstPlayerDelta());
    }

    @Test
    void reversingPlayersAndOutcomePreservesTheSameFinalRatings() {
        RatingChange firstWins = EloCalculator.calculate(
                1200,
                1600,
                RatingOutcome.WIN
        );

        RatingChange secondLoses = EloCalculator.calculate(
                1600,
                1200,
                RatingOutcome.LOSS
        );

        assertEquals(
                firstWins.firstPlayerRating(),
                secondLoses.secondPlayerRating()
        );
        assertEquals(
                firstWins.secondPlayerRating(),
                secondLoses.firstPlayerRating()
        );
        assertEquals(
                firstWins.firstPlayerDelta(),
                -secondLoses.firstPlayerDelta()
        );
    }

    @Test
    void roundingStillPreservesTheTotalRating() {
        RatingChange change = EloCalculator.calculate(
                1373,
                1528,
                RatingOutcome.DRAW
        );

        assertEquals(
                1373L + 1528L,
                (long) change.firstPlayerRating()
                        + change.secondPlayerRating()
        );
        assertEquals(
                change.firstPlayerDelta(),
                change.firstPlayerRating() - 1373
        );
        assertEquals(
                -change.firstPlayerDelta(),
                change.secondPlayerRating() - 1528
        );
    }

    @Test
    void missingOutcomeIsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> EloCalculator.calculate(1200, 1200, null)
        );
    }
}