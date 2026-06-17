package com.company.officecommute.domain.commute;

import java.time.LocalDate;
import java.util.Objects;

public class DailyWorkDuration {

    private final LocalDate date;

    private final WorkingMinutes workingMinutes;

    private final boolean usingDayOff;

    public DailyWorkDuration(LocalDate date, long workingMinutes, boolean usingDayOff) {
        this(date, new WorkingMinutes(workingMinutes), usingDayOff);
    }

    public DailyWorkDuration(LocalDate date, WorkingMinutes workingMinutes, boolean usingDayOff) {
        this.date = date;
        this.workingMinutes = workingMinutes;
        this.usingDayOff = usingDayOff;
    }

    public LocalDate getDate() {
        return date;
    }

    public boolean isUsingDayOff() {
        return usingDayOff;
    }

    public long getWorkingMinutes() {
        return workingMinutes.getWorkingMinutes();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        DailyWorkDuration that = (DailyWorkDuration) object;
        return usingDayOff == that.usingDayOff
                && Objects.equals(date, that.date)
                && Objects.equals(workingMinutes, that.workingMinutes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, workingMinutes, usingDayOff);
    }
}
