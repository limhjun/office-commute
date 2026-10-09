package com.company.officecommute.dto.closing.request;

import com.company.officecommute.domain.closing.LegacyResolution;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.YearMonth;

public record MonthlyClosingCreateRequest(

        @NotNull(message = "마감할 월은 필수입니다.")
        YearMonth yearMonth,

        LegacyResolution legacyResolution,

        @Size(max = 1000, message = "운영 메모는 1000자 이하여야 합니다.")
        String note
) {
}
