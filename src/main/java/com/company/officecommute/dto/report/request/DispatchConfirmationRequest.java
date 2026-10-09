package com.company.officecommute.dto.report.request;

import com.company.officecommute.domain.report.ReportKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.YearMonth;

public record DispatchConfirmationRequest(

        @NotNull(message = "대상 월은 필수입니다.")
        YearMonth yearMonth,

        @NotNull(message = "보고서 종류는 필수입니다.")
        ReportKind kind,

        @NotNull(message = "확인 결과는 필수입니다.")
        DispatchConfirmationOutcome outcome,

        @NotBlank(message = "확인 근거는 필수입니다.")
        @Size(max = 1000, message = "확인 근거는 1000자 이하여야 합니다.")
        String note
) {
}
