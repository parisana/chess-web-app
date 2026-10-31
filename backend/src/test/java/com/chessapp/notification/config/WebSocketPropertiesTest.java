package com.chessapp.notification.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The customized-routing test matters because it catches accidentally hardcoded destinations.
 * The failure tests verify that missing or invalid configuration stops startup, while boundary tests protect valid extreme values.
 * */
class WebSocketPropertiesTest {

    private static final String PREFIX = "app.websocket.";

    @Test
    void bindsSettingsAndDerivesDestinations() {
        runner(validProperties()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(WebSocketProperties.class);

            var properties = context.getBean(WebSocketProperties.class);

            assertThat(properties.endpoint()).isEqualTo("/api/v1/ws");
            assertThat(properties.allowedOrigin())
                    .isEqualTo("http://localhost:4200");
            assertThat(properties.applicationPrefix()).isEqualTo("/app");
            assertThat(properties.userPrefix()).isEqualTo("/user");
            assertThat(properties.brokerPrefix()).isEqualTo("/queue");
            assertThat(properties.updatesPath()).isEqualTo("/updates");

            assertThat(properties.timeToFirstMessage())
                    .isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.timeToFirstMessageMillis())
                    .isEqualTo(10_000);
            assertThat(properties.heartbeatInterval())
                    .isEqualTo(Duration.ofSeconds(10));

            assertThat(properties.updatesDestination())
                    .isEqualTo("/queue/updates");
            assertThat(properties.subscriptionDestination())
                    .isEqualTo("/user/queue/updates");
        });
    }

    @Test
    void derivesDestinationsFromCustomizedRoutingSettings() {
        var values = validProperties();
        values.put("application-prefix", "/commands");
        values.put("user-prefix", "/private");
        values.put("broker-prefix", "/notifications");
        values.put("updates-path", "/games/changed");

        runner(values).run(context -> {
            assertThat(context).hasNotFailed();

            var properties = context.getBean(WebSocketProperties.class);

            assertThat(properties.updatesDestination())
                    .isEqualTo("/notifications/games/changed");
            assertThat(properties.subscriptionDestination())
                    .isEqualTo("/private/notifications/games/changed");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "endpoint",
            "allowed-origin",
            "application-prefix",
            "user-prefix",
            "broker-prefix",
            "updates-path",
            "time-to-first-message",
            "heartbeat-interval"
    })
    void missingRequiredSettingPreventsStartup(String setting) {
        var values = validProperties();
        values.remove(setting);

        assertValidationFailure(values, "WebSocket");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "endpoint",
            "application-prefix",
            "user-prefix",
            "broker-prefix",
            "updates-path"
    })
    void rejectsNonliteralPathsForEveryRoutingSetting(String setting) {
        for (String invalidPath : new String[] {
                "",
                "/",
                "updates",
                "/updates/",
                "/queue//updates",
                "/queue/*",
                "/queue/**",
                "/queue/{playerId}",
                "/queue/updates?player=1",
                "/queue/updates#fragment",
                "/queue/my updates"
        }) {
            var values = validProperties();
            values.put(setting, invalidPath);

            assertValidationFailure(values, "literal slash-prefixed path");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "application-prefix, /user",
            "application-prefix, /user/commands",
            "application-prefix, /us",
            "broker-prefix, /app",
            "broker-prefix, /app/events",
            "user-prefix, /queue",
            "user-prefix, /queue/private"
    })
    void rejectsEqualOrOverlappingRoutingPrefixes(
            String setting,
            String value
    ) {
        var values = validProperties();
        values.put(setting, value);

        assertValidationFailure(values, "routing prefixes must not overlap");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "*",
            "localhost:4200",
            "ws://localhost:4200",
            "ftp://localhost",
            "http://localhost:4200/",
            "https://example.com/path",
            "https://example.com?query=value",
            "https://example.com#fragment",
            "https://user:password@example.com",
            "http://localhost:65536"
    })
    void rejectsInvalidOrigins(String origin) {
        var values = validProperties();
        values.put("allowed-origin", origin);

        assertValidationFailure(values, "WebSocket allowed-origin");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:4200",
            "https://chess.example.com",
            "https://chess.example.com:8443"
    })
    void acceptsHttpAndHttpsOrigins(String origin) {
        var values = validProperties();
        values.put("allowed-origin", origin);

        runner(values).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(WebSocketProperties.class)
                    .allowedOrigin()).isEqualTo(origin);
        });
    }

    @ParameterizedTest
    @CsvSource({
            "time-to-first-message, 0ms",
            "time-to-first-message, -1ms",
            "time-to-first-message, 2147483648ms",
            "heartbeat-interval, 0ms",
            "heartbeat-interval, -1s",
            "heartbeat-interval, 999ms",
            "heartbeat-interval, 300001ms"
    })
    void rejectsDurationsOutsideSupportedRanges(
            String setting,
            String value
    ) {
        var values = validProperties();
        values.put(setting, value);

        assertValidationFailure(values, "WebSocket " + setting);
    }

    @ParameterizedTest
    @CsvSource({
            "1ms, 1s, 1, 1000",
            "2147483647ms, 300s, 2147483647, 300000"
    })
    void acceptsInclusiveDurationBoundaries(
            String firstMessage,
            String heartbeat,
            int expectedFirstMessageMillis,
            long expectedHeartbeatMillis
    ) {
        var values = validProperties();
        values.put("time-to-first-message", firstMessage);
        values.put("heartbeat-interval", heartbeat);

        runner(values).run(context -> {
            assertThat(context).hasNotFailed();

            var properties = context.getBean(WebSocketProperties.class);

            assertThat(properties.timeToFirstMessageMillis())
                    .isEqualTo(expectedFirstMessageMillis);
            assertThat(properties.heartbeatInterval().toMillis())
                    .isEqualTo(expectedHeartbeatMillis);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "time-to-first-message",
            "heartbeat-interval"
    })
    void rejectsUnparseableDurations(String setting) {
        var values = validProperties();
        values.put(setting, "not-a-duration");

        runner(values).run(context -> {
            assertThat(context).hasFailed();

            // Conversion can fail before the record constructor is called.
            // Avoid coupling this test to conversion exception wording.
            assertThat(context.getStartupFailure()).isNotNull();
        });
    }

    private static ApplicationContextRunner runner(
            Map<String, String> values
    ) {
        String[] propertyValues = values.entrySet().stream()
                .map(entry -> PREFIX + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);

        // Register only property binding. No component scan, database,
        // HTTP server or WebSocket broker is required for these tests.
        return new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues(propertyValues);
    }

    private static Map<String, String> validProperties() {
        // Each test receives its own map so overrides cannot leak.
        return new LinkedHashMap<>(Map.of(
                "endpoint", "/api/v1/ws",
                "allowed-origin", "http://localhost:4200",
                "application-prefix", "/app",
                "user-prefix", "/user",
                "broker-prefix", "/queue",
                "updates-path", "/updates",
                "time-to-first-message", "10s",
                "heartbeat-interval", "10s"
        ));
    }

    private static void assertValidationFailure(
            Map<String, String> values,
            String expectedMessage
    ) {
        runner(values).run(context -> {
            assertThat(context).hasFailed();

            // Spring wraps constructor validation failures during binding.
            // Check the underlying rejection rather than wrapper wording.
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasStackTraceContaining(expectedMessage);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WebSocketProperties.class)
    static class PropertiesConfiguration {
    }
}