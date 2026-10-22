package com.chessapp.game.repository;

import com.chessapp.game.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author pari on 10/22/26
 */
public interface GameRepository extends JpaRepository<Game, Long> {
}
