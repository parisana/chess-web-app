package com.chessapp.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
/**
 * @author pari on 10/29/26
 */
@ConfigurationProperties(prefix = "app.websocket")
public record WebSocketProperties(
        String endpoint,
        String allowedOrigin,
        String applicationPrefix,
        String userPrefix,
        String brokerPrefix,
        String updatesPath,
        Duration timeToFirstMessage,
        Duration heartbeatInterval
) {

    // Require literal paths: no wildcards, empty segments or trailing slash.
    private static final Pattern PATH_PATTERN =
            Pattern.compile(
                    "/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*"
            );

    public WebSocketProperties {
        requirePath(endpoint, "endpoint");
        requirePath(applicationPrefix, "application-prefix");
        requirePath(userPrefix, "user-prefix");
        requirePath(brokerPrefix, "broker-prefix");
        requirePath(updatesPath, "updates-path");
        requireOrigin(allowedOrigin);

        // Routing prefixes must not overlap because prefix-based
        // dispatch could otherwise send a message to the wrong handler.
        List<String> prefixes = List.of(
                applicationPrefix,
                userPrefix,
                brokerPrefix
        );

        for (int i = 0; i < prefixes.size(); i++) {
            for (int j = i + 1; j < prefixes.size(); j++) {
                String first = prefixes.get(i);
                String second = prefixes.get(j);

                if (first.startsWith(second)
                        || second.startsWith(first)) {
                    throw new IllegalArgumentException(
                            "WebSocket routing prefixes must not overlap"
                    );
                }
            }
        }

        // Spring's transport setter takes an int number of milliseconds.
        if (timeToFirstMessage == null
                || timeToFirstMessage.compareTo(
                Duration.ofMillis(1)
        ) < 0
                || timeToFirstMessage.compareTo(
                Duration.ofMillis(Integer.MAX_VALUE)
        ) > 0) {
            throw new IllegalArgumentException(
                    "WebSocket time-to-first-message must be between "
                            + "1ms and " + Integer.MAX_VALUE + "ms"
            );
        }

        if (heartbeatInterval == null
                || heartbeatInterval.compareTo(
                Duration.ofSeconds(1)
        ) < 0
                || heartbeatInterval.compareTo(
                Duration.ofMillis(300_000)
        ) > 0) {
            throw new IllegalArgumentException(
                    "WebSocket heartbeat-interval must be between "
                            + "1s and 300s"
            );
        }
    }

    public String updatesDestination() {
        return brokerPrefix + updatesPath;
    }

    public String subscriptionDestination() {
        return userPrefix + updatesDestination();
    }

    public int timeToFirstMessageMillis() {
        return Math.toIntExact(timeToFirstMessage.toMillis());
    }

    private static void requirePath(String value, String name) {
        if (value == null
                || !PATH_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "WebSocket " + name
                            + " must be a literal slash-prefixed path"
                            + " without a trailing slash"
            );
        }
    }

    private static void requireOrigin(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "WebSocket allowed-origin is required"
            );
        }

        URI origin = URI.create(value);

        boolean validScheme =
                "http".equals(origin.getScheme())
                        || "https".equals(origin.getScheme());

        String path = origin.getRawPath();

        if (!validScheme
                || origin.getHost() == null
                || origin.getRawUserInfo() != null
                || origin.getRawQuery() != null
                || origin.getRawFragment() != null
                || (path != null && !path.isEmpty())
                || origin.getPort() > 65535) {
            throw new IllegalArgumentException(
                    "WebSocket allowed-origin must be an HTTP(S) origin"
                            + " without a path, query or fragment"
            );
        }
    }
}
