package com.company.officecommute.domain.report;

/** 같은 월이라도 최초 보고서와 기능 도입 전 발송 월의 정정본은 식별·파일·발송 이력을 따로 둔다. */
public enum ReportKind {
    ORIGINAL,
    CORRECTION
}
