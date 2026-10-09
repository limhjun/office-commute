package com.company.officecommute.service.overtime;

import com.company.officecommute.domain.report.ReportFinality;

import java.time.YearMonth;

/**
 * 초과근무 보고서 엑셀 파일명. 확정본 다운로드와 발송 메일 첨부가 <b>같은 이름</b>을 내보내야
 * 대표·관리자가 두 경로에서 받은 파일을 같은 것으로 식별한다. 참고용·정정본은 이름으로 구분한다 —
 * 참고용 파일이 확정본처럼 보관·전달되는 일을 막는다.
 */
public final class OverTimeReportFileName {

    private OverTimeReportFileName() {
    }

    public static String of(YearMonth yearMonth) {
        return of(yearMonth, ReportFinality.FINAL);
    }

    public static String of(YearMonth yearMonth, ReportFinality finality) {
        String base = yearMonth.getYear() + "년" + yearMonth.getMonthValue() + "월_초과근무보고서";
        return switch (finality) {
            case FINAL -> base + ".xlsx";
            case CORRECTION -> base + "_정정본.xlsx";
            case REFERENCE -> base + "_참고용.xlsx";
        };
    }
}
