package com.company.officecommute.domain.employee;

public class EmployeeNotFoundException extends RuntimeException {
    public EmployeeNotFoundException(Long employeeId) {
        super(String.format("존재하지 않는 직원입니다: %d", employeeId));
    }
}
