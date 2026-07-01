package com.company.officecommute.dto.auth.response;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.team.Team;

public record CurrentUserResponse(
        Long employeeId,
        String name,
        String email,
        String role,
        Long teamId,
        String teamName
) {
    public static CurrentUserResponse from(Employee employee) {
        Team team = employee.getTeam();
        return new CurrentUserResponse(
                employee.getEmployeeId(),
                employee.getName(),
                employee.getEmail(),
                employee.getRole().name(),
                team != null ? team.getTeamId() : null,
                team != null ? team.getName() : null
        );
    }
}
