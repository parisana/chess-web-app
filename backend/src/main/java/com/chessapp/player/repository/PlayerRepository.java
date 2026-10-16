package com.chessapp.player.repository;

import com.chessapp.player.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * @author pari on 10/16/26
 */
public interface PlayerRepository extends JpaRepository<Player, Long> {
    Optional<Player> findByUsername(String username);

    Optional<Player> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
