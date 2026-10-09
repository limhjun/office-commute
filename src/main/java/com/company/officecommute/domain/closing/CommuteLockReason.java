package com.company.officecommute.domain.closing;

public enum CommuteLockReason {

    /** 마감된 보고서의 집계 입력 기간(전월 말 참조 기간 포함). */
    MONTH_CLOSED(ClosingErrorCode.CLOSING_PERIOD_LOCKED),

    /** 수신 여부 불명(DELIVERY_COMMITTED) 보고서의 입력 기간. 운영자 확인 전 임시 차단. */
    DELIVERY_UNCERTAIN(ClosingErrorCode.REPORT_DELIVERY_UNCERTAIN);

    private final ClosingErrorCode errorCode;

    CommuteLockReason(ClosingErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public ClosingException toException() {
        return new ClosingException(errorCode);
    }
}
