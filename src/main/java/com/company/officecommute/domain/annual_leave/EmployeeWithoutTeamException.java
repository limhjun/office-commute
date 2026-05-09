package com.company.officecommute.domain.annual_leave;

public class EmployeeWithoutTeamException extends RuntimeException {
    public EmployeeWithoutTeamException() {
        super("팀이 배정되지 않은 직원은 연차를 신청할 수 없습니다.");
    }
}
