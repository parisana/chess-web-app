package com.chessapp.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification.delivery")
public record NotificationDeliveryProperties(int workers, int queueCapacity) {

    public NotificationDeliveryProperties {
        if (workers < 1) {
            throw new IllegalArgumentException("Notification delivery workers must be positive");
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("Notification delivery queue-capacity must be positive");
        }
    }
}
