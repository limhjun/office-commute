package com.company.officecommute.dto.annual_leave.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record AnnualLeaveEnrollRequest(

        @NotEmpty(message = "신청할 연차 일자는 필수입니다.")
        List<@NotNull(message = "연차 일자는 null일 수 없습니다.") LocalDate> wantedDates
) {
}
