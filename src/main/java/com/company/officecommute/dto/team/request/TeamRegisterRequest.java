package com.company.officecommute.dto.team.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record TeamRegisterRequest(
        @NotBlank(message = "팀 이름은 비어있을 수 없습니다")
        String teamName,

        String managerName,

        @PositiveOrZero(message = "팀 연차 등록 기준은 0 이상이어야 합니다.")
        Integer annualLeaveCriteria
) {
    public TeamRegisterRequest {
        if (annualLeaveCriteria == null) {
            annualLeaveCriteria = 0;
        }
    }
}
