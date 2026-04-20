package com.company.officecommute.controller.auth;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.dto.auth.request.LoginRequest;
import com.company.officecommute.service.employee.EmployeeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
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
    public void login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        Employee employee = employeeService.authenticate(request.email(), request.password());
        session.setAttribute("currentEmployeeId", employee.getEmployeeId());
        session.setAttribute("currentRole", employee.getRole());
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
