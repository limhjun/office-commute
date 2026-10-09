package com.company.officecommute.domain.commute;

public class CommuteEndWindowExpiredException extends RuntimeException {
    public CommuteEndWindowExpiredException() {
        super("출근 후 24시간이 지나 일반 퇴근할 수 없습니다. 퇴근 시각 정정을 신청해 주세요.");
    }
}
