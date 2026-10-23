package com.chessapp.player.entity;

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
@Table(name = "players")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Player extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30, unique = true)
    private String username;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    public Player(String username, String email) {
        this.username = username;
        this.email = email;
    }

    public void changeUsername(String username) {
        this.username = username;
    }
}
