package com.company.officecommute.service.closing;

import java.time.LocalDate;

/** 월 마감을 막는 미퇴근 기록 한 건. */
public record UnresolvedCommuteRow(
        Long commuteHistoryId,
        Long employeeId,
        String employeeCode,
        String employeeName,
        LocalDate workDate
) {
}
