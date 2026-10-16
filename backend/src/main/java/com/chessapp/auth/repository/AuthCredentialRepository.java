package com.chessapp.auth.repository;
import com.chessapp.auth.entity.AuthCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * @author pari on 10/16/26
 */
public interface AuthCredentialRepository
        extends JpaRepository<AuthCredential, Long> {

    Optional<AuthCredential> findByPlayerId(Long playerId);
}
