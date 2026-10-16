package com.chessapp.player.service;

import com.chessapp.player.dto.PlayerResponse;
import com.chessapp.player.entity.Player;
import com.chessapp.player.exception.DuplicatePlayerException;
import com.chessapp.player.exception.PlayerErrorCodes;
import com.chessapp.player.repository.PlayerRepository;
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

        return toPlayerResponse(player);
    }

    @Transactional(readOnly = true)
    public Optional<PlayerResponse> findByEmail(String email) {
        return playerRepository.findByEmail(email)
                .map(PlayerService::toPlayerResponse);
    }

    private static PlayerResponse toPlayerResponse(Player player) {
        return new PlayerResponse(
                player.getId(),
                player.getUsername(),
                player.getEmail()
        );
    }
}
