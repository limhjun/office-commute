package com.company.officecommute.domain.closing;

/**
 * 월 마감·보호 기간·보고서 발송 규칙 위반. 코드 이름이 그대로 API 오류 코드가 된다.
 */
public enum ClosingErrorCode {

    CLOSING_PERIOD_LOCKED(409, "마감된 보고서에 포함된 기간이라 변경할 수 없습니다."),
    REPORT_DELIVERY_UNCERTAIN(409, "보고서 수신 확인 전이라 이 기간은 임시로 변경할 수 없습니다."),
    CLOSING_MONTH_NOT_ENDED(400, "끝난 과거 월만 마감할 수 있습니다."),
    MONTH_ALREADY_CLOSED(409, "이미 마감된 월입니다."),
    CLOSING_HAS_UNRESOLVED(409, "집계 기간에 미퇴근 또는 승인 대기 정정 요청이 남아 있습니다."),
    LEGACY_RESOLUTION_REQUIRED(409, "기존 발송 월입니다. 정정 여부와 운영 메모를 함께 지정해 주세요."),
    LEGACY_RESOLUTION_NOT_APPLICABLE(400, "기존 발송 월이 아니므로 정정 여부를 지정할 수 없습니다."),
    CLOSING_PREVIOUS_LEGACY_MONTH_PENDING(409, "집계 기간이 겹치는 이전 기존 발송 월을 먼저 정리해 주세요."),
    DISPATCH_NOT_FOUND(404, "발송 이력이 없습니다."),
    DISPATCH_NOT_UNCERTAIN(409, "수신 확인이 필요한 발송 상태가 아닙니다."),
    REPORT_FILE_NOT_FOUND(404, "보관된 확정본이 없습니다.");

    private final int httpStatus;
    private final String defaultMessage;

    ClosingErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
