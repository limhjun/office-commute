package com.company.officecommute.dto.annual_leave.response;

import com.company.officecommute.domain.annual_leave.AnnualLeave;

import java.time.LocalDate;
import java.util.List;

public record AnnualLeaveEnrollmentResponse(
        Long annualLeaveId,
        LocalDate enrolledDate
) {
    public static List<AnnualLeaveEnrollmentResponse> listFrom(List<AnnualLeave> annualLeaves) {
        return annualLeaves.stream()
                .map(AnnualLeaveEnrollmentResponse::from)
                .toList();
    }

    public static AnnualLeaveEnrollmentResponse from(AnnualLeave annualLeave) {
        return new AnnualLeaveEnrollmentResponse(annualLeave.getId(), annualLeave.getDate());
    }
}
