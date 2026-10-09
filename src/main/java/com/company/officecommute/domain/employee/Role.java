package com.company.officecommute.domain.employee;

public enum Role {
    MANAGER, MEMBER, COMMUTE_APPROVER;

    /**
     * 이 역할의 정정 요청을 처리할 지정 승인자의 역할. MEMBER 는 지정 승인자 없이 모든 MANAGER 가 처리한다.
     * MANAGER 와 COMMUTE_APPROVER 는 서로를 승인하므로 자기 승인은 막지만 독립적인 제3자 검토는 아니다.
     */
    public Role requiredCorrectionApproverRole() {
        return switch (this) {
            case MEMBER -> null;
            case MANAGER -> COMMUTE_APPROVER;
            case COMMUTE_APPROVER -> MANAGER;
        };
    }
}
