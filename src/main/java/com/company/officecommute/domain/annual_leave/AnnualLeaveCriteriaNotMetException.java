package com.company.officecommute.domain.annual_leave;

public class AnnualLeaveCriteriaNotMetException extends RuntimeException {
    public AnnualLeaveCriteriaNotMetException() {
        super("팀의 연차 등록 기준을 충족하지 못합니다.");
    }
}
