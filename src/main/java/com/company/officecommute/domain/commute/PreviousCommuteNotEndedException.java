package com.company.officecommute.domain.commute;

public class PreviousCommuteNotEndedException extends RuntimeException {
    public PreviousCommuteNotEndedException() {
        super("이전 근무가 아직 종료되지 않았습니다.");
    }
}
