package com.chessapp.game.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
/**
 * @author pari on 11/2/26
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GameClockProperties.class)
@EnableScheduling
public class GameClockConfig {
}
