package com.company.officecommute.service.employee;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.domain.team.Team;

import java.time.LocalDate;

public class EmployeeBuilder {
    private Long id;
    private Team team;
    private String name;
    private Role role;
    private LocalDate birthday;
    private LocalDate workStartDate;
    private String employeeCode;
    private String pin;

    public EmployeeBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public EmployeeBuilder withTeam(Team team) {
        this.team = team;
        return this;
    }

    public EmployeeBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public EmployeeBuilder withRole(Role role) {
        this.role = role;
        return this;
    }

    public EmployeeBuilder withBirthday(LocalDate date) {
        this.birthday = date;
        return this;
    }

    public EmployeeBuilder withStartDate(LocalDate date) {
        this.workStartDate = date;
        return this;
    }

    public EmployeeBuilder withEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
        return this;
    }

    public EmployeeBuilder withPin(String pin) {
        this.pin = pin;
        return this;
    }

    public Employee build() {
        return new Employee(id, team, name, role, birthday, workStartDate, employeeCode, pin);
    }
}
