package com.company.officecommute.dto.employee.response;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.team.Team;

import java.time.LocalDate;

public record EmployeeFindResponse(
        Long employeeId,
        Long teamId,
        String teamName,
        String name,
        String role,
        LocalDate birthday,
        LocalDate workStartDate,
        String timezone
) {
    public static EmployeeFindResponse from(Employee employee) {
        Team team = employee.getTeam();
        return new EmployeeFindResponse(
                employee.getEmployeeId(),
                team != null ? team.getTeamId() : null,
                team != null ? team.getName() : null,
                employee.getName(),
                employee.getRole().name(),
                employee.getBirthday(),
                employee.getWorkStartDate(),
                employee.getTimezone()
        );
    }
}
