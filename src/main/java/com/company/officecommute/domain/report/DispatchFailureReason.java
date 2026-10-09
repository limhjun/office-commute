package com.company.officecommute.domain.report;

/**
 * 발송 실패의 분류. 재시도가 의미를 가지려면 관리자가 "무엇을 고쳐야 하는지" 또는
 * "고칠 것이 없으니 기다리면 되는지"를 알아야 하므로, 실패를 뭉뚱그리지 않고 나눈다.
 */
public enum DispatchFailureReason {

    /** 월 마감 전이라 발송을 보류했다. 장애가 아니다 — 근태 관리자가 월 마감하면 그 직후 발송된다. */
    MONTH_NOT_CLOSED("월 마감 전이라 발송을 보류했습니다. 미퇴근·승인 대기 정정을 해결하고 월 마감하면 발송됩니다."),

    /** 퇴근 미마감 기록이 있어 대표 발송을 보류했다. 마감하면 다음 재시도에서 자동 발송된다. */
    UNCLOSED_COMMUTES("퇴근 미마감 기록이 있어 발송을 보류했습니다. 마감하면 다음 재시도에서 자동 발송됩니다."),

    /** 공휴일 API가 응답하지 않아 집계를 신뢰할 수 없다. 사람이 할 조치는 없다. */
    HOLIDAY_DATA_UNAVAILABLE("공휴일 API를 이용할 수 없어 집계를 중단했습니다. 조치 없이 재시도됩니다."),

    /** SMTP 오류. 계정·서버 설정을 봐야 한다. */
    MAIL_SEND_FAILED("메일 발송에 실패했습니다. SMTP 계정과 서버 설정을 확인해 주세요."),

    /** 그 외. 스택 트레이스는 로그에 남는다. */
    UNEXPECTED("예상하지 못한 오류로 발송에 실패했습니다. 서버 로그를 확인해 주세요.");

    private final String managerGuidance;

    DispatchFailureReason(String managerGuidance) {
        this.managerGuidance = managerGuidance;
    }

    public String getManagerGuidance() {
        return managerGuidance;
    }
}
