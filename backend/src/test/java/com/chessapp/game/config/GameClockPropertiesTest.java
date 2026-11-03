package com.chessapp.game.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * @author pari on 11/2/26
 */
class GameClockPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(GameClockConfig.class);

    @Test
    void bindsConfiguredTimeControl() {
        runner.withPropertyValues(
                "app.game.clock.initial-time=10m"
        ).run(context -> {
            assertThat(context).hasNotFailed();

            assertThat(context.getBean(GameClockProperties.class)
                    .initialTimeMillis())
                    .isEqualTo(600_000);
        });
    }

    @Test
    void missingTimeControlPreventsStartup() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(Objects.requireNonNull(context.getStartupFailure()))
                    .hasRootCauseInstanceOf(
                            IllegalArgumentException.class
                    );
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0ms",
            "-1ms",
            "PT0.000000001S",
            "86400001ms",
            "not-a-duration"
    })
    void invalidTimeControlPreventsStartup(String value) {
        runner.withPropertyValues(
                "app.game.clock.initial-time=" + value
        ).run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1ms", "24h"})
    void acceptsInclusiveBoundaries(String value) {
        runner.withPropertyValues(
                "app.game.clock.initial-time=" + value
        ).run(context -> assertThat(context).hasNotFailed());
    }
}