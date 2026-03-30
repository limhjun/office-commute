package com.company.officecommute.controller.annual_leave;

import com.company.officecommute.dto.annual_leave.request.AnnualLeaveEnrollRequest;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveEnrollmentResponse;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveGetRemainingResponse;
import com.company.officecommute.service.annual_leave.AnnualLeaveService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AnnualLeaveController {

    private final AnnualLeaveService annualLeaveService;

    public AnnualLeaveController(AnnualLeaveService annualLeaveService) {
        this.annualLeaveService = annualLeaveService;
    }

    @PostMapping("/annual-leave")
    public List<AnnualLeaveEnrollmentResponse> enrollAnnualLeave(
            @RequestAttribute("currentEmployeeId") Long employeeId,
            @RequestBody AnnualLeaveEnrollRequest enrollRequest
    ) {
        return annualLeaveService.enrollAnnualLeave(employeeId, enrollRequest.wantedDates());
    }

    @GetMapping("/annual-leave")
    public AnnualLeaveGetRemainingResponse getRemainingAnnualLeaves(@RequestAttribute("currentEmployeeId") Long employeeId) {
        return annualLeaveService.getRemainingAnnualLeaves(employeeId);
    }
}
