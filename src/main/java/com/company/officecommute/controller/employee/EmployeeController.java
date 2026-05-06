package com.company.officecommute.controller.employee;

import com.company.officecommute.auth.ManagerOnly;
import com.company.officecommute.dto.employee.request.EmployeeChangeTeamRequest;
import com.company.officecommute.dto.employee.request.EmployeeSaveRequest;
import com.company.officecommute.dto.employee.response.EmployeeFindResponse;
import com.company.officecommute.dto.employee.response.EmployeeRegisterResponse;
import com.company.officecommute.service.employee.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @ManagerOnly
    @PostMapping("/employee")
    public ResponseEntity<EmployeeRegisterResponse> saveEmployee(@Valid @RequestBody EmployeeSaveRequest request) {
        EmployeeRegisterResponse response = employeeService.registerEmployee(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ManagerOnly
    @GetMapping("/employee")
    public List<EmployeeFindResponse> findAllEmployee() {
        return employeeService.findAllEmployee();
    }

    @ManagerOnly
    @PutMapping("/employee/{employeeId}/team")
    public void changeTeam(
            @PathVariable Long employeeId,
            @RequestBody EmployeeChangeTeamRequest request
    ) {
        employeeService.changeTeam(employeeId, request.teamId());
    }
}
