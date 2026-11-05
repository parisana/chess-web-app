package com.chessapp.player.service;

import com.chessapp.player.dto.PlayerGameDetails;
import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.entity.Player;
import com.chessapp.player.entity.PlayerStatistics;
import com.chessapp.player.exception.DuplicatePlayerException;
import com.chessapp.player.exception.PlayerErrorCodes;
import com.chessapp.player.repository.PlayerRepository;
import com.chessapp.player.repository.PlayerStatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * @author pari on 10/16/26
 */
@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerStatisticsRepository playerStatisticsRepository;

    @Transactional
    public PlayerResponse createPlayer(String username, String email) {
        if (playerRepository.existsByUsername(username)) {
            throw new DuplicatePlayerException(
                    PlayerErrorCodes.USERNAME_ALREADY_EXISTS,
                    "Username is already taken"
            );
        }

        if (playerRepository.existsByEmail(email)) {
            throw new DuplicatePlayerException(
                    PlayerErrorCodes.EMAIL_ALREADY_EXISTS,
                    "Email is already registered"
            );
        }
        Player player = playerRepository.save(
                new Player(username, email)
        );
        // Ensure the parent row exists before inserting statistics.
        // Both inserts remain part of the registration/caller transaction.
        playerRepository.flush();

        playerStatisticsRepository.saveAndFlush(
                new PlayerStatistics(player.getId())
        );

        return toPlayerResponse(player);
    }

    @Transactional(readOnly = true)
    public Optional<PlayerResponse> findByEmail(String email) {
        return playerRepository.findByEmail(email)
                .map(PlayerService::toPlayerResponse);
    }

    @Transactional(readOnly = true)
    public Optional<PlayerResponse> findById(Long id) {
        return playerRepository.findById(id)
                .map(PlayerService::toPlayerResponse);
    }

    @Transactional
    public Optional<PlayerResponse> updateUsername(
            Long playerId,
            String username
    ) {
        return playerRepository.findById(playerId)
                .map(player -> {
                    // Keeping the current username is a successful no-op.
                    if (player.getUsername().equals(username)) {
                        return toPlayerResponse(player);
                    }

                    if (playerRepository.existsByUsername(username)) {
                        throw new DuplicatePlayerException(
                                PlayerErrorCodes.USERNAME_ALREADY_EXISTS,
                                "Username is already taken"
                        );
                    }

                    player.changeUsername(username);

                    // Execute the update before returning. The database unique
                    // constraint remains authoritative if requests race.
                    playerRepository.flush();

                    return toPlayerResponse(player);
                });
    }

    /**
     * Reads current details for the caller to capture when creating a game.
     * Uses ordinary reads; it does not lock both Players' statistics.
     */
    @Transactional(readOnly = true)
    public Optional<PlayerGameDetails> findGameDetails(Long playerId) {
        return playerRepository.findById(playerId)
                .map(player -> {
                    var statistics = playerStatisticsRepository
                            .findById(playerId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Statistics missing for Player " + playerId
                            ));

                    return new PlayerGameDetails(
                            player.getUsername(),
                            statistics.getRating()
                    );
                });
    }

    private static PlayerResponse toPlayerResponse(Player player) {
        return new PlayerResponse(
                player.getId(),
                player.getUsername(),
                player.getEmail()
        );
    }
}
