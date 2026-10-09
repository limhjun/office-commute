package com.company.officecommute.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 통합 테스트용 시계. 월·일 경계와 24시간 경계를 재현하려고 테스트 중에 시각을 옮긴다.
 */
public class MutableClock extends Clock {

    private volatile Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant instant) {
        this(instant, ZoneOffset.UTC);
    }

    private MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    public void advance(Duration duration) {
        this.instant = this.instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new DelegatingZoneClock(this, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    /** {@code withZone} 이후에도 원래 시계의 시각 변경을 따라간다. */
    private static final class DelegatingZoneClock extends Clock {

        private final MutableClock source;
        private final ZoneId zone;

        private DelegatingZoneClock(MutableClock source, ZoneId zone) {
            this.source = source;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new DelegatingZoneClock(source, zone);
        }

        @Override
        public Instant instant() {
            return source.instant();
        }
    }
}
