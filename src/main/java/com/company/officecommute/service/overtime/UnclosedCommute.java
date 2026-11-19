package com.company.officecommute.service.overtime;

import java.time.LocalDate;

/**
 * 퇴근이 찍히지 않은 출근 기록 한 건. 관리자에게 "무엇을 마감해야 하는지" 알려주려면
 * 건수만으로는 부족해 사번·이름·근무일이 필요하다.
 */
public record UnclosedCommute(
        String employeeCode,
        String employeeName,
        LocalDate workDate
) {
}
