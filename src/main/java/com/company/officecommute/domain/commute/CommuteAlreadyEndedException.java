package com.company.officecommute.domain.commute;

public class CommuteAlreadyEndedException extends RuntimeException {
    public CommuteAlreadyEndedException() {
        super("이미 퇴근 처리된 근무입니다.");
    }
}
