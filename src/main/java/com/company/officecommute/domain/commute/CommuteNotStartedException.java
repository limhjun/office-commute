package com.company.officecommute.domain.commute;

public class CommuteNotStartedException extends RuntimeException {
    public CommuteNotStartedException() {
        super("진행 중인 출근 기록이 없습니다.");
    }
}
