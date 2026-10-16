package com.chessapp.player.service;

import com.chessapp.player.entity.Player;
import com.chessapp.player.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author pari on 10/16/26
 */
@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;

    public Player createPlayer(String username, String email) {
        Player player = new Player(username, email);
        return playerRepository.save(player);
    }
}
