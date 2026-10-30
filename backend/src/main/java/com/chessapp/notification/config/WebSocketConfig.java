package com.chessapp.notification.config;

import com.chessapp.notification.security.StompOutboundSecurityInterceptor;
import com.chessapp.notification.security.StompSecurityInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketProperties.class)
@RequiredArgsConstructor
public class WebSocketConfig
        implements WebSocketMessageBrokerConfigurer {
    private TaskScheduler brokerTaskScheduler;
    private final StompSecurityInterceptor securityInterceptor;
    private final StompOutboundSecurityInterceptor outboundSecurityInterceptor;
    private final WebSocketProperties properties;

    // broker scheduler with lazy injection to avoid a configuration dependency cycle
    @Autowired
    public void setBrokerTaskScheduler(
            @Lazy
            @Qualifier("messageBrokerTaskScheduler")
            TaskScheduler brokerTaskScheduler
    ) {
        this.brokerTaskScheduler = brokerTaskScheduler;
    }

    @Override
    public void registerStompEndpoints(
            StompEndpointRegistry registry
    ) {
        registry.addEndpoint(properties.endpoint())
                .setAllowedOrigins(properties.allowedOrigin());
    }

    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry
    ) {
        long heartbeatMillis =
                properties.heartbeatInterval().toMillis();

        registry.enableSimpleBroker(properties.brokerPrefix())
                .setTaskScheduler(brokerTaskScheduler)
                .setHeartbeatValue(new long[] {
                        heartbeatMillis,
                        heartbeatMillis
                });

        registry.setApplicationDestinationPrefixes(
                properties.applicationPrefix()
        );

        registry.setUserDestinationPrefix(
                properties.userPrefix()
        );
    }

    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration
    ) {
        registration.interceptors(securityInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(
            ChannelRegistration registration
    ) {
        registration.interceptors(outboundSecurityInterceptor);
    }

    @Override
    public void configureWebSocketTransport(
            WebSocketTransportRegistration registration
    ) {
        registration.setTimeToFirstMessage(
                properties.timeToFirstMessageMillis()
        );
    }
}