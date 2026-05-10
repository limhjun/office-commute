package com.company.officecommute.domain.commute;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"employee_id", "work_date"})
})
public class CommuteHistory {
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long commuteHistoryId;

    private Long employeeId;

    private ZonedDateTime workStartTime;

    private ZonedDateTime workEndTime;

    private long workingMinutes;

    private boolean usingDayOff;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "work_zone", nullable = false)
    private String workZone;

    private static final int ANNUAL_LEAVE_TIME = 0;

    private static final boolean IS_ANNUAL_LEAVE = true;

    protected CommuteHistory() {
    }

    // 연차일 때, 근무 이력을 나타내는 생성자
    public CommuteHistory(Long employeeId) {
        this(null, employeeId, ZonedDateTime.now(), ZonedDateTime.now(), ANNUAL_LEAVE_TIME, IS_ANNUAL_LEAVE, DEFAULT_ZONE);
    }

    public CommuteHistory(
            Long commuteHistoryId,
            Long employeeId,
            ZonedDateTime workStartTime,
            ZonedDateTime workEndTime,
            long workingMinutes
    ) {
        this(commuteHistoryId, employeeId, workStartTime, workEndTime, workingMinutes, false, DEFAULT_ZONE);
    }

    public CommuteHistory(
            Long commuteHistoryId,
            Long employeeId,
            ZonedDateTime workStartTime,
            ZonedDateTime workEndTime,
            long workingMinutes,
            ZoneId workZone
    ) {
        this(commuteHistoryId, employeeId, workStartTime, workEndTime, workingMinutes, false, workZone);
    }

    // 연차용 생성자
    public CommuteHistory(Long employeeId, LocalDate annualLeaveDate) {
        this(employeeId, annualLeaveDate, DEFAULT_ZONE);
    }

    public CommuteHistory(Long employeeId, LocalDate annualLeaveDate, ZoneId workZone) {
        this(null, employeeId, annualLeaveDate.atStartOfDay(workZone), annualLeaveDate.atStartOfDay(workZone), 0, true, workZone);
        this.workDate = annualLeaveDate;
    }

    public CommuteHistory(
            Long commuteHistoryId,
            Long employeeId,
            ZonedDateTime workStartTime,
            ZonedDateTime workEndTime,
            long workingMinutes,
            boolean usingDayOff
    ) {
        this(commuteHistoryId, employeeId, workStartTime, workEndTime, workingMinutes, usingDayOff, DEFAULT_ZONE);
    }

    public CommuteHistory(
            Long commuteHistoryId,
            Long employeeId,
            ZonedDateTime workStartTime,
            ZonedDateTime workEndTime,
            long workingMinutes,
            boolean usingDayOff,
            ZoneId workZone
    ) {
        Objects.requireNonNull(workZone, "workZone은 null일 수 없습니다");
        this.commuteHistoryId = commuteHistoryId;
        this.employeeId = employeeId;
        this.workStartTime = workStartTime;
        this.workEndTime = workEndTime;
        this.workingMinutes = workingMinutes;
        this.usingDayOff = usingDayOff;
        this.workZone = workZone.getId();
        this.workDate = (workStartTime != null)
                ? workStartTime.withZoneSameInstant(workZone).toLocalDate()
                : LocalDate.now(workZone);
    }

    public CommuteHistory endWork(ZonedDateTime workEndTime) {
        if (this.workStartTime == null) {
            throw new CommuteNotStartedException();
        }
        if (this.workEndTime != null) {
            throw new CommuteAlreadyEndedException();
        }
        if (workEndTime.isBefore(this.workStartTime)) {
            throw new InvalidCommuteRangeException();
        }
        long workingMinutes = Duration.between(this.workStartTime, workEndTime).toMinutes();
        WorkingMinutes validatedWorkingMinutes = new WorkingMinutes(workingMinutes);
        this.workingMinutes = validatedWorkingMinutes.getWorkingMinutes();
        this.workEndTime = workEndTime;
        return this;
    }

    public Detail toDetail() {
        if (isAnnualLeaveDate()) {
            return new Detail(this.workStartTimeToLocalDate(), ANNUAL_LEAVE_TIME, this.usingDayOff);
        }
        return new Detail(this.workStartTimeToLocalDate(), this.workingMinutes, this.usingDayOff);
    }

    private boolean isAnnualLeaveDate() {
        return this.usingDayOff;
    }

    public LocalDate workStartTimeToLocalDate() {
        return workStartTime.toLocalDate();
    }

    public boolean endTimeIsNull() {
        return this.workEndTime == null;
    }


    public ZonedDateTime getWorkEndTime() {
        return workEndTime;
    }

    public long getWorkingMinutes() {
        return workingMinutes;
    }

    public String getWorkZone() {
        return workZone;
    }

    public ZoneId getWorkZoneId() {
        return ZoneId.of(workZone);
    }
}
