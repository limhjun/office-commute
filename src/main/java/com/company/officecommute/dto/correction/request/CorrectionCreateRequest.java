package com.company.officecommute.dto.correction.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record CorrectionCreateRequest(

        @NotNull(message = "근태 기록 ID는 필수입니다.")
        Long commuteHistoryId,

        @NotNull(message = "근태 기록 버전은 필수입니다.")
        Long commuteVersion,

        // OffsetDateTime 이라 오프셋 없는 값은 역직렬화 단계에서 INVALID_JSON 이 된다.
        @NotNull(message = "정정할 종료 시각은 필수입니다.")
        OffsetDateTime requestedWorkEndTime,

        @NotBlank(message = "정정 사유는 필수입니다.")
        @Size(max = 500, message = "정정 사유는 500자 이하여야 합니다.")
        String reason
) {
}
