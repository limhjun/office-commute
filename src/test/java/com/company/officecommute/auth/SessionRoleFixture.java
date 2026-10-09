package com.company.officecommute.auth;

import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.repository.employee.EmployeeRepository;

import java.util.Optional;

import static org.mockito.BDDMockito.given;

/**
 * 컨트롤러 테스트의 세션 직원(1 = MANAGER, 2 = MEMBER)을 {@link AuthInterceptor}의 DB 역할 조회에 맞춘다.
 * 인터셉터가 세션 역할 대신 현재 DB 역할을 쓰므로, 세션만 만들어서는 권한 판정이 재현되지 않는다.
 */
public final class SessionRoleFixture {

    public static final long MANAGER_ID = 1L;
    public static final long MEMBER_ID = 2L;

    private SessionRoleFixture() {
    }

    public static void stubSessionRoles(EmployeeRepository employeeRepository) {
        given(employeeRepository.findRoleById(MANAGER_ID)).willReturn(Optional.of(Role.MANAGER));
        given(employeeRepository.findRoleById(MEMBER_ID)).willReturn(Optional.of(Role.MEMBER));
    }
}
