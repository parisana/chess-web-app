package com.chessapp.matchmaking.service;

import com.chessapp.game.service.GameService;
import com.chessapp.matchmaking.dto.QueueStatusResponse;
import com.chessapp.matchmaking.exception.MatchmakingPlayerUnavailableException;
import com.chessapp.matchmaking.repository.MatchmakingLockRepository;
import com.chessapp.matchmaking.repository.MatchmakingQueueRepository;
import com.chessapp.player.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author pari on 10/21/26
 */
@Service
@RequiredArgsConstructor
public class MatchmakingService {

    private final MatchmakingQueueRepository queueRepository;
    private final MatchmakingLockRepository lockRepository;
    private final PlayerService playerService;
    private final GameService gameService;
    private final Clock clock;

    @Transactional
    public QueueStatusResponse join(Long playerId) {
        // Acquire before accessing queue state so concurrent joins cannot
        // both miss the other transaction's uncommitted entry.
        lockRepository.lockQueue();
        requirePlayer(playerId);

        var activeGameId = gameService.findActiveGameId(playerId);

        if (activeGameId.isPresent()) {
            // Remove any stale membership without cancelling the game.
            queueRepository.deleteEntryByPlayerId(playerId);

            return QueueStatusResponse.matched(activeGameId.get());
        }

        queueRepository.insertOrKeepExisting(
                playerId,
                clock.instant()
        );

        pairAvailablePlayers();

        return currentStatus(playerId);
    }

    // Leaving the queue must not cancel an established game.
    @Transactional
    public void leave(Long playerId) {
        lockRepository.lockQueue();
        requirePlayer(playerId);

        queueRepository.deleteEntryByPlayerId(playerId);
    }

    @Transactional(readOnly = true)
    public QueueStatusResponse status(Long playerId) {
        lockRepository.lockQueue();
        requirePlayer(playerId);

        return currentStatus(playerId);
    }

    private void pairAvailablePlayers() {
        while (true) {
            var entries = queueRepository.findOldestTwoForUpdate();

            if (entries.size() < 2) {
                return;
            }

            // A separate game-creation caller might have made a queued
            // player ineligible. Remove stale entries before pairing.
            boolean removedStaleEntry = false;

            for (var entry : entries) {
                if (gameService.findActiveGameId(entry.getPlayerId())
                        .isPresent()) {

                    queueRepository.deleteEntryByPlayerId(
                            entry.getPlayerId()
                    );
                    removedStaleEntry = true;
                }
            }

            if (removedStaleEntry) {
                continue;
            }

            Long firstPlayerId = entries.get(0).getPlayerId();
            Long secondPlayerId = entries.get(1).getPlayerId();

            boolean firstIsWhite =
                    ThreadLocalRandom.current().nextBoolean();

            gameService.createGame(
                    firstIsWhite ? firstPlayerId : secondPlayerId,
                    firstIsWhite ? secondPlayerId : firstPlayerId
            );

            int firstDeleted =
                    queueRepository.deleteEntryByPlayerId(firstPlayerId);

            int secondDeleted =
                    queueRepository.deleteEntryByPlayerId(secondPlayerId);

            if (firstDeleted != 1 || secondDeleted != 1) {
                throw new IllegalStateException(
                        "Paired queue entries could not be removed"
                );
            }
        }
    }

    private QueueStatusResponse currentStatus(Long playerId) {
        var activeGameId = gameService.findActiveGameId(playerId);

        return activeGameId.map(QueueStatusResponse::matched)
                .orElseGet(() -> queueRepository.findByPlayerId(playerId)
                    .map(entry -> QueueStatusResponse.waiting(
                            entry.getEnqueuedAt()
                    ))
                .orElseGet(QueueStatusResponse::notQueued));

    }

    private void requirePlayer(Long playerId) {
        playerService.findById(playerId)
                .orElseThrow(
                        MatchmakingPlayerUnavailableException::new
                );
    }
}
