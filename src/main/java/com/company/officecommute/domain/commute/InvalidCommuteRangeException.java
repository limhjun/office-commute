package com.company.officecommute.domain.commute;

public class InvalidCommuteRangeException extends RuntimeException {
    public InvalidCommuteRangeException() {
        super("퇴근 시간이 출근 시간보다 이릅니다.");
    }
}
