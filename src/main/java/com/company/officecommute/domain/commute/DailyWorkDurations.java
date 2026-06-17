package com.company.officecommute.domain.commute;

import java.util.List;

public class DailyWorkDurations {

    private final List<DailyWorkDuration> dailyWorkDurations;

    public DailyWorkDurations(List<DailyWorkDuration> dailyWorkDurations) {
        this.dailyWorkDurations = dailyWorkDurations;
    }

    public long sumWorkingMinutes() {
        return dailyWorkDurations.stream()
                .mapToLong(DailyWorkDuration::getWorkingMinutes)
                .sum();
    }
}
