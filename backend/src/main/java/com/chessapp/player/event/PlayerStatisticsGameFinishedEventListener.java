package com.chessapp.player.event;

import com.chessapp.game.event.GameFinishedEvent;
import com.chessapp.player.rating.RatingOutcome;
import com.chessapp.player.service.PlayerStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
/**
 * @author pari on 11/1/26
 */
@Component
@RequiredArgsConstructor
public class PlayerStatisticsGameFinishedEventListener {

    private final PlayerStatisticsService statisticsService;

    /**
     * Runs synchronously inside the publishing game transaction.
     * Do not add @Async or switch to AFTER_COMMIT: statistics failures
     * must propagate and roll back the completion.
     */
    @EventListener
    public void onGameFinished(GameFinishedEvent event) {
        RatingOutcome whiteOutcome = switch (event.result()) {
            case WHITE_WIN -> RatingOutcome.WIN;
            case BLACK_WIN -> RatingOutcome.LOSS;
            case DRAW -> RatingOutcome.DRAW;
        };

        statisticsService.recordFinishedGame(
                event.gameId(),
                event.whitePlayerId(),
                event.blackPlayerId(),
                whiteOutcome
        );
    }
}
