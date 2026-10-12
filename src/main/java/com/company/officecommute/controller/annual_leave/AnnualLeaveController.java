package com.company.officecommute.controller.annual_leave;

import com.company.officecommute.auth.AuthAttributes;
import com.company.officecommute.dto.annual_leave.request.AnnualLeaveEnrollRequest;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveEnrollmentResponse;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveGetRemainingResponse;
import com.company.officecommute.service.annual_leave.AnnualLeaveService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AnnualLeaveController {

    private final AnnualLeaveService annualLeaveService;

    public AnnualLeaveController(AnnualLeaveService annualLeaveService) {
        this.annualLeaveService = annualLeaveService;
    }

    @PostMapping("/annual-leave")
    public List<AnnualLeaveEnrollmentResponse> enrollAnnualLeave(
            @RequestAttribute(AuthAttributes.CURRENT_EMPLOYEE_ID) Long employeeId,
            @Valid @RequestBody AnnualLeaveEnrollRequest enrollRequest
    ) {
        return annualLeaveService.enrollAnnualLeave(employeeId, enrollRequest.wantedDates());
    }

    @GetMapping("/annual-leave")
    public AnnualLeaveGetRemainingResponse getRemainingAnnualLeaves(@RequestAttribute(AuthAttributes.CURRENT_EMPLOYEE_ID) Long employeeId) {
        return annualLeaveService.getRemainingAnnualLeaves(employeeId);
    }
}
