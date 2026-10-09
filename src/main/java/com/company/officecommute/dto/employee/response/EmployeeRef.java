package com.company.officecommute.dto.employee.response;

import com.company.officecommute.domain.employee.Employee;

public record EmployeeRef(
        Long employeeId,
        String name,
        String employeeCode
) {
    public static EmployeeRef from(Employee employee) {
        if (employee == null) {
            return null;
        }
        return new EmployeeRef(employee.getEmployeeId(), employee.getName(), employee.getEmployeeCode());
    }
}
