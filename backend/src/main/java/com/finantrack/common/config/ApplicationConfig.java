package com.finantrack.common.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FinanTrackProperties.class)
public class ApplicationConfig {

    /** Fonte única de "hoje" nas regras financeiras; substituível nos testes. */
    @Bean
    public Clock clock(FinanTrackProperties properties) {
        return Clock.system(properties.zoneId());
    }
}
