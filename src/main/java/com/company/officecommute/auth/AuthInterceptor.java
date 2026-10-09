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

        Long employeeId = (Long) session.getAttribute("currentEmployeeId");
        if (employeeId == null || session.getAttribute("currentRole") == null) {
            throw new AuthenticationFailedException();
        }

        // 역할은 세션이 아니라 매 요청 DB 에서 읽는다. 역할이 바뀐 뒤 이전 세션의 역할로 승인·마감하는
        // 우회를 막는다. 직원이 사라졌으면 세션도 더는 유효하지 않다.
        Role role = employeeRepository.findRoleById(employeeId)
                .orElseThrow(AuthenticationFailedException::new);
        if (session.getAttribute("currentRole") != role) {
            session.setAttribute("currentRole", role);
        }

        request.setAttribute("currentEmployeeId", employeeId);
        request.setAttribute("currentRole", role);

        if (handler instanceof HandlerMethod handlerMethod) {
            ManagerOnly managerOnly = handlerMethod.getMethodAnnotation(ManagerOnly.class);
            if (managerOnly != null && role != Role.MANAGER) {
                throw new ForbiddenException();
            }
        }

        return true;
    }
}
