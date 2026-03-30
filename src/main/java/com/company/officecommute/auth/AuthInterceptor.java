package com.company.officecommute.auth;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.service.employee.EmployeeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class AuthInterceptor implements HandlerInterceptor {

    private final EmployeeService employeeService;

    public AuthInterceptor(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String employeeCode = request.getHeader("X-Employee-Code");
        String pin = request.getHeader("X-Employee-Pin");

        if (employeeCode == null || employeeCode.isBlank() || pin == null || pin.isBlank()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        Employee employee;
        try {
            employee = employeeService.authenticate(employeeCode, pin);
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        request.setAttribute("currentEmployeeId", employee.getEmployeeId());
        request.setAttribute("currentRole", employee.getRole());

        if (handler instanceof HandlerMethod handlerMethod) {
            ManagerOnly managerOnly = handlerMethod.getMethodAnnotation(ManagerOnly.class);
            if (managerOnly != null && employee.getRole() != Role.MANAGER) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                return false;
            }
        }

        return true;
    }
}
