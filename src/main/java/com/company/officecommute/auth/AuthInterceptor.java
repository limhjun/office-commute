package com.company.officecommute.auth;

import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.repository.employee.EmployeeRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class AuthInterceptor implements HandlerInterceptor {

    private final EmployeeRepository employeeRepository;

    public AuthInterceptor(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new AuthenticationFailedException();
        }

        Long employeeId = (Long) session.getAttribute(AuthAttributes.CURRENT_EMPLOYEE_ID);
        if (employeeId == null) {
            throw new AuthenticationFailedException();
        }

        Role role = employeeRepository.findRoleById(employeeId)
                .orElseThrow(AuthenticationFailedException::new);

        request.setAttribute(AuthAttributes.CURRENT_EMPLOYEE_ID, employeeId);

        if (handler instanceof HandlerMethod handlerMethod) {
            ManagerOnly managerOnly = handlerMethod.getMethodAnnotation(ManagerOnly.class);
            if (managerOnly != null && role != Role.MANAGER) {
                throw new ForbiddenException();
            }
        }

        return true;
    }
}
