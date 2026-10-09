package com.company.officecommute.dto.correction.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CorrectionRejectRequest(

        @NotBlank(message = "반려 사유는 필수입니다.")
        @Size(max = 500, message = "반려 사유는 500자 이하여야 합니다.")
        String reason
) {
}
