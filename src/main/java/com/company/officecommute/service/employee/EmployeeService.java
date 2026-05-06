package com.company.officecommute.service.employee;

import com.company.officecommute.auth.AuthenticationFailedException;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.EmployeeAlreadyExistsException;
import com.company.officecommute.domain.employee.EmployeeNotFoundException;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.domain.team.TeamNotFoundException;
import com.company.officecommute.dto.employee.request.EmployeeSaveRequest;
import com.company.officecommute.dto.employee.response.EmployeeFindResponse;
import com.company.officecommute.dto.employee.response.EmployeeRegisterResponse;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.team.TeamRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final TeamRepository teamRepository;
    private final PasswordEncoder passwordEncoder;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            TeamRepository teamRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.employeeRepository = employeeRepository;
        this.teamRepository = teamRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public EmployeeRegisterResponse registerEmployee(EmployeeSaveRequest request) {
        if (employeeRepository.existsByEmployeeCode(request.employeeCode())) {
            throw EmployeeAlreadyExistsException.ofEmployeeCode(request.employeeCode());
        }
        if (employeeRepository.existsByEmail(request.email())) {
            throw EmployeeAlreadyExistsException.ofEmail(request.email());
        }
        Team team = resolveTeam(request.teamId());
        Employee employee = Employee.register(
                request.name(),
                request.role(),
                request.birthday(),
                request.workStartDate(),
                request.employeeCode(),
                request.email(),
                passwordEncoder.encode(request.password()),
                team
        );
        try {
            Employee saved = employeeRepository.save(employee);
            return new EmployeeRegisterResponse(saved.getEmployeeId());
        } catch (DataIntegrityViolationException e) {
            if (employeeRepository.existsByEmployeeCode(request.employeeCode())) {
                throw EmployeeAlreadyExistsException.ofEmployeeCode(request.employeeCode());
            }
            throw EmployeeAlreadyExistsException.ofEmail(request.email());
        }
    }

    @Transactional
    public void changeTeam(Long employeeId, Long teamId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        Team team = resolveTeam(teamId);
        employee.changeTeam(team);
    }

    @Transactional(readOnly = true)
    public List<EmployeeFindResponse> findAllEmployee() {
        return employeeRepository.findAllWithTeam()
                .stream()
                .map(EmployeeFindResponse::from)
                .toList();
    }

    public Employee authenticate(String email, String password) {
        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationFailedException("존재하지 않는 이메일입니다."));
        if (!passwordEncoder.matches(password, employee.getPassword())) {
            throw new AuthenticationFailedException("비밀번호가 일치하지 않습니다.");
        }
        return employee;
    }

    private Team resolveTeam(Long teamId) {
        if (teamId == null) {
            return null;
        }
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));
    }
}
