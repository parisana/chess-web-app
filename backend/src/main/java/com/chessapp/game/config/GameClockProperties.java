package com.chessapp.game.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
/**
 * @author pari on 11/2/26
 */
@ConfigurationProperties(prefix = "app.game.clock")
public record GameClockProperties(Duration initialTime) {

    public GameClockProperties {
        if (initialTime == null
                || initialTime.compareTo(Duration.ofMillis(1)) < 0
                || initialTime.compareTo(Duration.ofDays(1)) > 0
                || initialTime.getNano() % 1_000_000 != 0) {
            throw new IllegalArgumentException(
                    "Game clock initial-time must be a whole-millisecond "
                            + "duration between 1ms and 24h"
            );
        }
    }

    public long initialTimeMillis() {
        return initialTime.toMillis();
    }
}
