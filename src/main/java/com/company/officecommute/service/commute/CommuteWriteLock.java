package com.company.officecommute.service.commute;

import com.company.officecommute.domain.employee.EmployeeNotFoundException;
import com.company.officecommute.repository.employee.EmployeeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 근태 쓰기의 공통 잠금. 잠금 대상은 근태를 소유한 직원 행이다.
 * <ul>
 *     <li>출근·퇴근·연차·정정 신청/승인/반려/취소: 소유 직원 한 행</li>
 *     <li>월 마감·퇴사·승인 담당자 지정: 전체 직원 행(ID 오름차순)</li>
 * </ul>
 * 한 행 잠금끼리는 직원이 다르면 서로 막지 않고, 전체 잠금은 모든 근태 쓰기와 직렬화된다 — 마감 검사 직후
 * 끼어든 쓰기는 마감 커밋 뒤에 실행되어 보호 기간 검사에 걸린다.
 * <p>
 * 반드시 트랜잭션의 <b>첫 문장</b>으로 부른다. MySQL REPEATABLE READ 의 일관 읽기 스냅샷은 첫 일반 읽기에서
 * 만들어지므로, 잠금보다 먼저 읽으면 앞선 잠금 보유자의 커밋을 보지 못한다.
 */
@Component
public class CommuteWriteLock {

    private final EmployeeRepository employeeRepository;

    public CommuteWriteLock(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockEmployee(Long employeeId) {
        employeeRepository.lockById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAllEmployees() {
        employeeRepository.lockAll();
    }
}
