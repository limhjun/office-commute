package com.company.officecommute.domain.commute;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class CommuteHistoryFixture {

    private CommuteHistoryFixture() {
    }

    public static CommuteHistory open(Long commuteHistoryId, Long employeeId, ZonedDateTime workStartTime) {
        return open(commuteHistoryId, employeeId, workStartTime, workStartTime.getZone());
    }

    public static CommuteHistory open(
            Long commuteHistoryId,
            Long employeeId,
            ZonedDateTime workStartTime,
            ZoneId workZone
    ) {
        CommuteHistory commuteHistory = CommuteHistory.registerWorkStart(employeeId, workStartTime, workZone);
        setId(commuteHistory, commuteHistoryId);
        return commuteHistory;
    }

    public static CommuteHistory ended(
            Long commuteHistoryId,
            Long employeeId,
            ZonedDateTime workStartTime,
            ZonedDateTime workEndTime
    ) {
        return ended(commuteHistoryId, employeeId, workStartTime, workEndTime, workStartTime.getZone());
    }

    public static CommuteHistory ended(
            Long commuteHistoryId,
            Long employeeId,
            ZonedDateTime workStartTime,
            ZonedDateTime workEndTime,
            ZoneId workZone
    ) {
        CommuteHistory commuteHistory = open(commuteHistoryId, employeeId, workStartTime, workZone);
        commuteHistory.endWork(workEndTime);
        return commuteHistory;
    }

    public static CommuteHistory annualLeave(Long employeeId, LocalDate annualLeaveDate, ZoneId workZone) {
        return CommuteHistory.registerAnnualLeave(employeeId, annualLeaveDate, workZone);
    }

    public static CommuteHistory notStarted(Long commuteHistoryId, Long employeeId) {
        CommuteHistory commuteHistory = new CommuteHistory();
        setId(commuteHistory, commuteHistoryId);
        ReflectionTestUtils.setField(commuteHistory, "employeeId", employeeId);
        return commuteHistory;
    }

    private static void setId(CommuteHistory commuteHistory, Long commuteHistoryId) {
        ReflectionTestUtils.setField(commuteHistory, "commuteHistoryId", commuteHistoryId);
    }
}
