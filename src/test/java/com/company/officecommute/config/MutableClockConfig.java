package com.company.officecommute.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

@TestConfiguration
public class MutableClockConfig {

    /** 2026-10-09 12:00 KST. 9월은 끝난 과거 월, 10월은 진행 중인 월이다. */
    public static final Instant DEFAULT_NOW = Instant.parse("2026-10-09T03:00:00Z");

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock(DEFAULT_NOW);
    }
}
