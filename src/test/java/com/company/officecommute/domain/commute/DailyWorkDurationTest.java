package com.company.officecommute.domain.commute;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DailyWorkDurationTest {

    @Test
    void equalsAndHashCode() {
        DailyWorkDuration dailyWorkDuration = new DailyWorkDuration(LocalDate.of(2026, 6, 17), 480L, false);
        DailyWorkDuration sameDailyWorkDuration = new DailyWorkDuration(LocalDate.of(2026, 6, 17), 480L, false);

        assertThat(dailyWorkDuration).isEqualTo(sameDailyWorkDuration);
        assertThat(dailyWorkDuration).hasSameHashCodeAs(sameDailyWorkDuration);
    }
}
