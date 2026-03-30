package com.company.officecommute.service.employee;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.dto.employee.request.EmployeeSaveRequest;
import com.company.officecommute.dto.employee.request.EmployeeUpdateTeamNameRequest;
import com.company.officecommute.dto.employee.response.EmployeeFindResponse;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.team.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final TeamRepository teamRepository;

    public EmployeeService(EmployeeRepository employeeRepository, TeamRepository teamRepository) {
        this.employeeRepository = employeeRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional
    public void registerEmployee(EmployeeSaveRequest request) {
        if (employeeRepository.existsByEmployeeCode(request.employeeCode())) {
            throw new IllegalArgumentException("이미 존재하는 직원 코드입니다.");
        }
        Employee employee = new Employee(
                request.name(),
                request.role(),
                request.birthday(),
                request.workStartDate(),
                request.employeeCode(),
                request.pin()
        );
        employeeRepository.save(employee);
    }

    @Transactional(readOnly = true)
    public List<EmployeeFindResponse> findAllEmployee() {
        return employeeRepository.findEmployeeHierarchy()
                .stream()
                .map(EmployeeFindResponse::from)
                .toList();
    }

    @Transactional
    public void updateEmployeeTeamName(EmployeeUpdateTeamNameRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 직원입니다."));
        String wantedTeamName = request.teamName();
        Team team = teamRepository.findByName(wantedTeamName)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 팀입니다."));
        employee.changeTeam(team);
    }

    public Employee authenticate(String employeeCode, String pin) {
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사번입니다"));
        if (!employee.matchesPin(pin)) {
            throw new IllegalArgumentException("PIN이 일치하지 않습니다.");
        }
        return employee;
    }

}
