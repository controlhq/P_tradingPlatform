package com.ex.tradingPlatform.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Random;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(MarketProperties.class)
public class MarketConfig {

    /** A bean so tests can supply a seeded or stubbed generator. */
    @Bean
    Random random() {
        return new Random();
    }
}
