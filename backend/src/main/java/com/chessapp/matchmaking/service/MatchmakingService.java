package com.chessapp.matchmaking.service;

import com.chessapp.matchmaking.dto.QueueStatusResponse;
import com.chessapp.matchmaking.exception.MatchmakingPlayerUnavailableException;
import com.chessapp.matchmaking.repository.MatchmakingQueueRepository;
import com.chessapp.player.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * @author pari on 10/21/26
 */
@Service
@RequiredArgsConstructor
public class MatchmakingService {

    private final MatchmakingQueueRepository queueRepository;
    private final PlayerService playerService;
    private final Clock clock;

    @Transactional
    public QueueStatusResponse join(Long playerId) {
        requirePlayer(playerId);

        queueRepository.insertOrKeepExisting(
                playerId,
                clock.instant()
        );

        // The unexpected missing row is an internal failure. It should not silently become NOT_QUEUED after a successful join.
        var entry = queueRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new IllegalStateException(
                        "Queue entry missing after successful join"
                ));

        return QueueStatusResponse.waiting(entry.getEnqueuedAt());
    }

    @Transactional
    public void leave(Long playerId) {
        requirePlayer(playerId);

        queueRepository.deleteEntryByPlayerId(playerId);
    }

    @Transactional(readOnly = true)
    public QueueStatusResponse status(Long playerId) {
        requirePlayer(playerId);

        return queueRepository.findByPlayerId(playerId)
                .map(entry -> QueueStatusResponse.waiting(
                        entry.getEnqueuedAt()
                ))
                .orElseGet(QueueStatusResponse::notQueued);
    }

    // Player access stays behind PlayerService. Matchmaking does not import player entities or repositories.
    private void requirePlayer(Long playerId) {
        playerService.findById(playerId)
                .orElseThrow(
                        MatchmakingPlayerUnavailableException::new
                );
    }
}
