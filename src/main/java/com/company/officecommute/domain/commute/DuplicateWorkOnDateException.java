package com.company.officecommute.domain.commute;

import java.time.LocalDate;

public class DuplicateWorkOnDateException extends RuntimeException {

    public DuplicateWorkOnDateException(LocalDate workDate) {
        super("해당 일자에 이미 출근 기록이 존재합니다: " + workDate);
    }

    public DuplicateWorkOnDateException(LocalDate workDate, Throwable cause) {
        super("해당 일자에 이미 출근 기록이 존재합니다: " + workDate, cause);
    }
}
