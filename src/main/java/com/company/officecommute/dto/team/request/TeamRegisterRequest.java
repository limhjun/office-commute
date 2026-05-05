package com.company.officecommute.dto.team.request;

import jakarta.validation.constraints.NotBlank;

public record TeamRegisterRequest(
        @NotBlank(message = "팀 이름은 비어있을 수 없습니다")
        String teamName,

        String managerName
) {
}
