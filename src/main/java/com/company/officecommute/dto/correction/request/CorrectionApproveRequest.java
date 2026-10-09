package com.company.officecommute.dto.correction.request;

import jakarta.validation.constraints.Size;

public record CorrectionApproveRequest(

        @Size(max = 500, message = "승인 의견은 500자 이하여야 합니다.")
        String comment
) {
}
