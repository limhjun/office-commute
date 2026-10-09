package com.company.officecommute.domain.report;

/**
 * 보고서 파일의 성격. 같은 집계 코드로 만들어도 마감 전 참고용과 마감 후 보관본은 다른 문서다 —
 * 파일명과 시트 상단 문구로 구분해야 수신자가 참고용을 확정본으로 오인하지 않는다.
 */
public enum ReportFinality {

    /** 현재 데이터로 매번 다시 집계한 참고용. 마감 여부와 관계없이 확정본이 아니다. */
    REFERENCE,

    /** 월 마감 후 보관·발송하는 최초 확정본. */
    FINAL,

    /** 기능 도입 전 발송 월을 정정 후 마감해 별도로 보내는 정정본. */
    CORRECTION;

    public static ReportFinality of(ReportKind kind) {
        return switch (kind) {
            case ORIGINAL -> FINAL;
            case CORRECTION -> CORRECTION;
        };
    }
}
