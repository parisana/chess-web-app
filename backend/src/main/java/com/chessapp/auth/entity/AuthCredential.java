package com.chessapp.auth.entity;

import com.chessapp.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author pari on 10/16/26
 */
@Getter
@Entity
@Table(name = "auth_credentials")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthCredential extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

//    we're using a Long playerId instead of @OneToOne Player.
//    This avoids unnecessarily coupling the authentication persistence model to the player entity.
    @Column(name = "player_id", nullable = false, unique = true)
    private Long playerId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    public AuthCredential(Long playerId, String passwordHash) {
        this.playerId = playerId;
        this.passwordHash = passwordHash;
    }
}