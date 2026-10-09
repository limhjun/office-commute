package com.company.officecommute.domain.correction;

/**
 * 정정 요청·승인 담당자 규칙 위반. 코드 이름이 그대로 API 오류 코드가 된다.
 */
public enum CorrectionErrorCode {

    COMMUTE_NOT_FOUND(404, "근태 기록을 찾을 수 없습니다."),
    CORRECTION_REQUEST_NOT_FOUND(404, "정정 요청을 찾을 수 없습니다."),
    CORRECTION_TARGET_DAY_OFF(400, "연차 기록은 정정할 수 없습니다."),
    CORRECTION_END_BEFORE_START(400, "종료 시각은 출근 시각 이후여야 합니다."),
    CORRECTION_END_IN_FUTURE(400, "종료 시각은 현재 시각 이전이어야 합니다."),
    CORRECTION_NO_CHANGE(400, "현재 종료 시각과 같습니다."),
    CORRECTION_OVERLAPS_NEXT_WORK(409, "종료 시각은 다음 근무의 출근 시각보다 늦을 수 없습니다."),
    CORRECTION_ALREADY_PENDING(409, "이 근태 기록에 승인 대기 중인 정정 요청이 있습니다."),
    CORRECTION_VERSION_CONFLICT(409, "근태 기록이 변경되었습니다. 최신 내용을 확인해 주세요."),
    CORRECTION_ALREADY_PROCESSED(409, "이미 처리된 정정 요청입니다."),
    CORRECTION_APPROVER_NOT_ASSIGNED(409, "정정 승인 담당자가 지정되지 않았습니다."),
    CORRECTION_SELF_APPROVAL(403, "본인의 정정 요청은 처리할 수 없습니다."),
    INVALID_CORRECTION_APPROVER(400, "지정할 수 없는 정정 승인 담당자입니다."),
    PENDING_CORRECTION_EXISTS(409, "승인 대기 중인 정정 요청을 먼저 처리해야 합니다.");

    private final int httpStatus;
    private final String defaultMessage;

    CorrectionErrorCode(int httpStatus, String defaultMessage) {
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
