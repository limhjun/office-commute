package com.company.officecommute.service.overtime;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashSet;
import java.util.Set;

public record OverTimePeriod(YearMonth targetMonth) {

    public LocalDate rangeStart() {
        return targetMonth.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public LocalDate rangeEnd() {
        return targetMonth.atEndOfMonth();
    }

    Set<YearMonth> requiredHolidayMonths() {
        // 월 1일이 월요일이면 두 값이 같다. `Set.of(..)`는 중복 인자에 예외를 던지므로 가변 Set에 add 한다
        Set<YearMonth> months = new LinkedHashSet<>();
        months.add(targetMonth);
        months.add(YearMonth.from(rangeStart()));
        return months;
    }
}
