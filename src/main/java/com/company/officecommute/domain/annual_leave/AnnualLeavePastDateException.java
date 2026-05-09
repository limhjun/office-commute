package com.company.officecommute.domain.annual_leave;

import java.time.LocalDate;

public class AnnualLeavePastDateException extends RuntimeException {
    public AnnualLeavePastDateException(LocalDate wantedDate) {
        super(String.format("(%s)는 지난 날짜입니다.", wantedDate));
    }
}
