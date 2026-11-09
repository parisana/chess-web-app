package com.chessapp.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableConfigurationProperties(NotificationDeliveryProperties.class)
public class NotificationDeliveryConfig {

    @Bean(name = "gameNotificationExecutor", defaultCandidate = false)
    public ThreadPoolTaskExecutor gameNotificationExecutor(NotificationDeliveryProperties properties) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.workers());
        executor.setMaxPoolSize(properties.workers());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("game-notification-");

        // Never use CallerRunsPolicy: delivery could then borrow another database
        // connection on the request thread before its game connection is released.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
