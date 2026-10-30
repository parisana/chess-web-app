package com.chessapp.game.repository;

import com.chessapp.game.entity.GameMove;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
/**
 * @author pari on 10/30/26
 */
public interface GameMoveRepository extends JpaRepository<GameMove, Long> {

    List<GameMove> findAllByGameIdOrderBySequenceNumberAsc(Long gameId);
}
