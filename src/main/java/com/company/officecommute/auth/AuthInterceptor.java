package com.company.officecommute.auth;

import com.company.officecommute.domain.employee.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        Long employeeId = (Long) session.getAttribute("currentEmployeeId");
        Role role = (Role) session.getAttribute("currentRole");

        if (employeeId == null || role == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        request.setAttribute("currentEmployeeId", employeeId);
        request.setAttribute("currentRole", role);

        if (handler instanceof HandlerMethod handlerMethod) {
            ManagerOnly managerOnly = handlerMethod.getMethodAnnotation(ManagerOnly.class);
            if (managerOnly != null && role != Role.MANAGER) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                return false;
            }
        }

        return true;
    }
}
