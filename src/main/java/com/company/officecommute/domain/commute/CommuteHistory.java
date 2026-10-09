package com.company.officecommute.domain.commute;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import com.company.officecommute.domain.correction.CorrectionErrorCode;
import com.company.officecommute.domain.correction.CorrectionException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(name = "uk_commute_history_employee_date", columnNames = {"employee_id", "work_date"})
})
public class CommuteHistory {
    /** 일반 퇴근 허용 시간. 출근부터 정확히 24시간까지 허용하고 초과하면 정정 승인이 필요하다. */
    private static final Duration REGULAR_END_WINDOW = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long commuteHistoryId;

    // 정정 요청이 신청 당시 원본을 가리키는 근거. 조건부 bulk UPDATE 는 @Version 을 자동 증가시키지 않으므로
    // 저장소의 쓰기 쿼리가 직접 1 올린다.
    @Version
    @Column(nullable = false)
    private long version;

    private Long employeeId;

    // Instant는 JDBC 바인딩이 항상 UTC로 정규화되므로 JVM 기본 타임존에 의존하지 않는다.
    // 달력 해석(날짜·표시)은 workZone과 조합해서만 한다.
    @Column(nullable = false)
    private Instant workStartTime;

    private Instant workEndTime;

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

    public static CommuteHistory registerWorkStart(Long employeeId, Instant workStartTime, ZoneId workZone) {
        return new CommuteHistory(null, employeeId, workStartTime, null, 0, false, workZone);
    }

    public static CommuteHistory registerAnnualLeave(Long employeeId, LocalDate annualLeaveDate, ZoneId workZone) {
        Instant startOfDay = annualLeaveDate.atStartOfDay(workZone).toInstant();
        return new CommuteHistory(
                null,
                employeeId,
                startOfDay,
                startOfDay,
                ANNUAL_LEAVE_TIME,
                IS_ANNUAL_LEAVE,
                workZone
        );
    }

    private CommuteHistory(
            Long commuteHistoryId,
            Long employeeId,
            Instant workStartTime,
            Instant workEndTime,
            long workingMinutes,
            boolean usingDayOff,
            ZoneId workZone
    ) {
        Objects.requireNonNull(workStartTime, "workStartTime은 null일 수 없습니다");
        Objects.requireNonNull(workZone, "workZone은 null일 수 없습니다");
        this.commuteHistoryId = commuteHistoryId;
        this.employeeId = employeeId;
        this.workStartTime = workStartTime;
        this.workEndTime = workEndTime;
        this.workingMinutes = workingMinutes;
        this.usingDayOff = usingDayOff;
        this.workZone = workZone.getId();
        this.workDate = workStartTime.atZone(workZone).toLocalDate();
    }

    public CommuteHistory endWork(Instant workEndTime) {
        this.workingMinutes = calculateWorkingMinutes(workEndTime);
        this.workEndTime = workEndTime;
        return this;
    }

    // 상태를 변경하지 않는다 — managed 엔티티에서 호출해도 dirty checking flush가 발생하지 않아야
    // 조건부 update(workEndTime IS NULL)가 유일한 쓰기 경로로 유지된다.
    public long calculateWorkingMinutes(Instant workEndTime) {
        if (this.workEndTime != null) {
            throw new CommuteAlreadyEndedException();
        }
        if (workEndTime.isBefore(this.workStartTime)) {
            throw new InvalidCommuteRangeException();
        }
        long workingMinutes = Duration.between(this.workStartTime, workEndTime).toMinutes();
        WorkingMinutes validatedWorkingMinutes = new WorkingMinutes(workingMinutes);
        return validatedWorkingMinutes.getWorkingMinutes();
    }

    /**
     * 일반 퇴근. 정정 승인과 달리 24시간 제한을 둔다 — 그보다 긴 근무는 사유와 승인으로만 기록한다.
     * {@link #calculateWorkingMinutes}와 마찬가지로 상태를 바꾸지 않는다.
     */
    public long calculateRegularEndMinutes(Instant now) {
        if (this.workEndTime != null) {
            throw new CommuteAlreadyEndedException();
        }
        if (now.isAfter(regularEndDeadline())) {
            throw new CommuteEndWindowExpiredException();
        }
        return calculateWorkingMinutes(now);
    }

    /**
     * 일반 퇴근으로 지금 종료할 수 있는가. 이 기록이 직원의 가장 최근 실제 근무인지는 호출자가 보장한다.
     * {@link #calculateRegularEndMinutes}와 같은 경계(정확히 24시간까지 허용)를 쓴다.
     */
    public boolean isRegularEndableAt(Instant now) {
        return !isAnnualLeaveDate()
                && this.workEndTime == null
                && !now.isBefore(this.workStartTime)
                && !now.isAfter(regularEndDeadline());
    }

    public OffsetDateTime zonedRegularEndDeadline() {
        return toWorkZone(regularEndDeadline());
    }

    private Instant regularEndDeadline() {
        return this.workStartTime.plus(REGULAR_END_WINDOW);
    }

    /**
     * 정정 종료 시각 검증과 근무 분 계산. 신청과 승인 시점 모두 같은 규칙으로 부른다.
     * 24시간 제한은 적용하지 않는다. {@code nextActualWorkStart}는 후속 실제 근무의 출근 시각(없으면 null)이다.
     */
    public long calculateCorrectedWorkingMinutes(Instant requestedEnd, Instant now, Instant nextActualWorkStart) {
        if (isAnnualLeaveDate()) {
            throw new CorrectionException(CorrectionErrorCode.CORRECTION_TARGET_DAY_OFF);
        }
        if (requestedEnd.isBefore(this.workStartTime)) {
            throw new CorrectionException(CorrectionErrorCode.CORRECTION_END_BEFORE_START);
        }
        if (requestedEnd.isAfter(now)) {
            throw new CorrectionException(CorrectionErrorCode.CORRECTION_END_IN_FUTURE);
        }
        if (nextActualWorkStart != null && requestedEnd.isAfter(nextActualWorkStart)) {
            throw new CorrectionException(CorrectionErrorCode.CORRECTION_OVERLAPS_NEXT_WORK);
        }
        if (requestedEnd.equals(this.workEndTime)) {
            throw new CorrectionException(CorrectionErrorCode.CORRECTION_NO_CHANGE);
        }
        return Duration.between(this.workStartTime, requestedEnd).toMinutes();
    }

    public DailyWorkDuration toDailyWorkDuration() {
        if (isAnnualLeaveDate()) {
            return new DailyWorkDuration(this.workDate, ANNUAL_LEAVE_TIME, this.usingDayOff);
        }
        return new DailyWorkDuration(this.workDate, this.workingMinutes, this.usingDayOff);
    }

    /**
     * 파생 상태. 저장 컬럼이나 시간 경과용 스케줄러 없이 조회 시점에 계산한다.
     * {@code latestActualWorkStart}는 같은 직원의 가장 최근 실제 근무 출근 시각으로, 조회 월 밖의 후속 근무도
     * 반영하려고 호출자가 따로 넘긴다(없으면 null).
     */
    public CommuteStatus status(Instant now, Instant latestActualWorkStart) {
        if (isAnnualLeaveDate()) {
            return CommuteStatus.DAY_OFF;
        }
        if (this.workEndTime != null) {
            return CommuteStatus.COMPLETED;
        }
        boolean hasLaterWork = latestActualWorkStart != null && latestActualWorkStart.isAfter(this.workStartTime);
        if (hasLaterWork || now.isAfter(regularEndDeadline())) {
            return CommuteStatus.CORRECTION_REQUIRED;
        }
        return CommuteStatus.IN_PROGRESS;
    }

    // 연차는 workStartTime/workEndTime이 자정으로 합성된 값이라 표시할 출퇴근 시각이 없다.
    public OffsetDateTime zonedWorkStartTime() {
        if (isAnnualLeaveDate()) {
            return null;
        }
        return toWorkZone(this.workStartTime);
    }

    public OffsetDateTime zonedWorkEndTime() {
        if (isAnnualLeaveDate() || this.workEndTime == null) {
            return null;
        }
        return toWorkZone(this.workEndTime);
    }

    private OffsetDateTime toWorkZone(Instant instant) {
        return instant.atZone(ZoneId.of(this.workZone)).toOffsetDateTime();
    }

    private boolean isAnnualLeaveDate() {
        return this.usingDayOff;
    }

    public Long getCommuteHistoryId() {
        return commuteHistoryId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public long getVersion() {
        return version;
    }

    public Instant getWorkStartTime() {
        return workStartTime;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public boolean isUsingDayOff() {
        return usingDayOff;
    }

    public boolean endTimeIsNull() {
        return this.workEndTime == null;
    }


    public Instant getWorkEndTime() {
        return workEndTime;
    }

    public long getWorkingMinutes() {
        return workingMinutes;
    }

    public String getWorkZone() {
        return workZone;
    }
}
