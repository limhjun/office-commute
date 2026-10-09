package com.company.officecommute.dto.closing.response;

import com.company.officecommute.domain.closing.MonthlyClosing;
import com.company.officecommute.domain.closing.MonthlyClosingType;
import com.company.officecommute.dto.employee.response.EmployeeRef;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

public record MonthlyClosingResponse(
        YearMonth yearMonth,
        MonthlyClosingType type,
        LocalDate rangeStart,
        LocalDate rangeEnd,
        EmployeeRef closedBy,
        Instant closedAt,
        String note
) {
    public static MonthlyClosingResponse from(MonthlyClosing closing, EmployeeRef closedBy) {
        return new MonthlyClosingResponse(
                closing.getTargetYearMonth(),
                closing.getClosingType(),
                closing.getRangeStart(),
                closing.getRangeEnd(),
                closedBy,
                closing.getClosedAt(),
                closing.getNote()
        );
    }
}
