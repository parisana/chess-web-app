package com.chessapp.game.service;

import com.chessapp.game.exception.GameNotFoundException;
import com.chessapp.game.exception.StaleGameVersionException;
import com.chessapp.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
/**
 * @author pari on 11/2/26
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.game.clock",
        name = "timeout-scan-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class GameTimeoutScanner {

    private static final int BATCH_SIZE = 100;

    private final GameRepository gameRepository;
    private final GameCommandService gameCommandService;
    private final Clock clock;

    /**
     * Deliberately has no surrounding transaction.
     * Each service call commits or rolls back independently.
     */
    @Scheduled(
            fixedDelayString =
                    "${app.game.clock.timeout-scan-interval:1s}"
    )
    public void scanAndExpireGames() {
        var candidates = gameRepository.findExpiredClockCandidates(
                clock.instant(),
                BATCH_SIZE
        );

        for (Long gameId : candidates) {
            try {
                gameCommandService.expireGame(gameId);
            } catch (StaleGameVersionException
                     | GameNotFoundException exception) {
                // A competing command changed or removed this candidate.
                // A later scan will reassess any game still eligible.
                log.debug(
                        "Timeout candidate changed: game {}",
                        gameId,
                        exception
                );
            } catch (RuntimeException exception) {
                // The service transaction has already rolled back.
                // Continue so one failed completion does not stop the batch.
                log.error(
                        "Could not complete expired game {}",
                        gameId,
                        exception
                );
            }
        }
    }
}
