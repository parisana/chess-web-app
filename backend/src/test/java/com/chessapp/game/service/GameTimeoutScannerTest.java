package com.chessapp.game.service;

import com.chessapp.game.exception.StaleGameVersionException;
import com.chessapp.game.repository.GameRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;


/**
 * @author pari on 11/3/26
 */
@ExtendWith(MockitoExtension.class)
class GameTimeoutScannerTest {

    private static final Instant NOW =
            Instant.parse("2026-11-03T08:00:00Z");

    @Mock private GameRepository repository;
    @Mock private GameService service;
    @Mock private Clock clock;

    @InjectMocks
    private GameTimeoutScanner scanner;

    @Test
    void emptyBatchDoesNotAttemptCompletion() {
        when(clock.instant()).thenReturn(NOW);
        when(repository.findExpiredClockCandidates(NOW, 100))
                .thenReturn(List.of());

        scanner.scanAndExpireGames();

        verifyNoInteractions(service);
    }

    @Test
    void staleCandidateDoesNotPreventLaterCompletionAttempts() {
        when(clock.instant()).thenReturn(NOW);
        when(repository.findExpiredClockCandidates(NOW, 100))
                .thenReturn(List.of(10L, 20L));

        when(service.expireGame(10L)).thenThrow(
                new StaleGameVersionException(
                        new IllegalStateException("Competing update")
                )
        );

        scanner.scanAndExpireGames();

        var order = inOrder(service);
        order.verify(service).expireGame(10L);
        order.verify(service).expireGame(20L);
        order.verifyNoMoreInteractions();
    }

    @Test
    void failedCompletionDoesNotStopTheRestOfTheBatch() {
        when(clock.instant()).thenReturn(NOW);
        when(repository.findExpiredClockCandidates(NOW, 100))
                .thenReturn(List.of(10L, 20L));

        when(service.expireGame(10L)).thenThrow(
                new ArithmeticException("Statistics overflow")
        );

        scanner.scanAndExpireGames();

        verify(service).expireGame(10L);
        verify(service).expireGame(20L);
        verifyNoMoreInteractions(service);
    }
}