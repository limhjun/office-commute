package com.company.officecommute.controller.auth;

import com.company.officecommute.auth.AuthenticationFailedException;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.dto.auth.request.LoginRequest;
import com.company.officecommute.dto.auth.response.CurrentUserResponse;
import com.company.officecommute.service.employee.EmployeeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final EmployeeService employeeService;

    public AuthController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping("/login")
    public void login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        Employee employee = employeeService.authenticate(request.email(), request.password());

        HttpSession existingSession = httpRequest.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        HttpSession newSession = httpRequest.getSession(true);
        newSession.setAttribute("currentEmployeeId", employee.getEmployeeId());
        newSession.setAttribute("currentRole", employee.getRole());
    }

    @GetMapping("/me")
    public CurrentUserResponse me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new AuthenticationFailedException("로그인이 필요합니다.");
        }
        Long employeeId = (Long) session.getAttribute("currentEmployeeId");
        if (employeeId == null) {
            throw new AuthenticationFailedException("로그인이 필요합니다.");
        }
        return employeeService.getCurrentUser(employeeId);
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
