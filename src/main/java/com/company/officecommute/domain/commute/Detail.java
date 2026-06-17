package com.company.officecommute.domain.commute;

import java.time.LocalDate;
import java.util.Objects;

public class Detail {

    private final LocalDate date;

    private final WorkingMinutes workingMinutes;

    private final boolean usingDayOff;

    public Detail(LocalDate date, long workingMinutes, boolean usingDayOff) {
        this(date, new WorkingMinutes(workingMinutes), usingDayOff);
    }

    public Detail(LocalDate date, WorkingMinutes workingMinutes, boolean usingDayOff) {
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
        Detail detail = (Detail) object;
        return usingDayOff == detail.usingDayOff
                && Objects.equals(date, detail.date)
                && Objects.equals(workingMinutes, detail.workingMinutes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, workingMinutes, usingDayOff);
    }
}
