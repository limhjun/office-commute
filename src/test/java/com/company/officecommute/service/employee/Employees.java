package com.company.officecommute.service.employee;

import com.company.officecommute.domain.employee.Employee;

import java.time.LocalDate;

import static com.company.officecommute.domain.employee.Role.MANAGER;

public class Employees {
    public static Employee employee = new EmployeeBuilder().withId(1L)
            .withName("hyungjunn")
            .withRole(MANAGER)
            .withBirthday(LocalDate.of(1998, 8, 18))
            .withStartDate(LocalDate.of(2024, 1, 1))
            .withEmployeeCode("EMP001")
            .withEmail("hyungjunn@company.com")
            .withPassword("password123")
            .build();
}
