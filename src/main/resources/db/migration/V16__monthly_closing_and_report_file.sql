-- 월 마감과 최종 보고서 보관 (Issue #52).

-- 월 마감. range_start~range_end 가 보호(집계 입력) 기간이다 — 월 1일이 속한 주의 월요일부터 월말까지라
-- 전월 말 기록도 포함한다. 마감 해제 경로는 없다.
-- closing_type: MANUAL | LEGACY_CONFIRMED | LEGACY_CORRECTED (LEGACY_* = 기능 도입 전 발송 월의 일회성 전환)
CREATE TABLE monthly_closing
(
    monthly_closing_id BIGINT        NOT NULL AUTO_INCREMENT,
    target_year_month  VARCHAR(7)    NOT NULL, -- 'yyyy-MM'
    closing_type       VARCHAR(20)   NOT NULL,
    range_start        DATE          NOT NULL,
    range_end          DATE          NOT NULL,
    closed_by_id       BIGINT        NOT NULL,
    closed_at          DATETIME(6)   NOT NULL,
    note               VARCHAR(1000) NULL,
    PRIMARY KEY (monthly_closing_id),
    CONSTRAINT uk_monthly_closing_year_month UNIQUE (target_year_month),
    CONSTRAINT fk_monthly_closing_closed_by FOREIGN KEY (closed_by_id) REFERENCES employee (employee_id)
);

CREATE INDEX idx_monthly_closing_range ON monthly_closing (range_start, range_end);

-- 발송 종류. 기존 행은 모두 최초 발송(ORIGINAL)이다. 기능 도입 전 발송 월의 정정본(CORRECTION)은
-- 원본 이력을 초기화하지 않고 별도 행으로 식별·중복 방지한다.
ALTER TABLE report_dispatch
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'ORIGINAL';

ALTER TABLE report_dispatch
    ADD CONSTRAINT uk_report_dispatch_year_month_kind UNIQUE (target_year_month, kind);

ALTER TABLE report_dispatch
    DROP INDEX uk_report_dispatch_year_month;

-- DELIVERY_COMMITTED(수신 불명)에 대한 운영자 확인 기록.
ALTER TABLE report_dispatch
    ADD COLUMN delivery_confirmed_by_id BIGINT NULL,
    ADD COLUMN delivery_confirmed_at DATETIME(6) NULL,
    ADD COLUMN delivery_confirmation_note VARCHAR(1000) NULL;

-- 발송에 사용하는 최종 Excel 보관본. 재시도·확정본 다운로드는 이 파일을 그대로 쓴다.
-- DB 에 보관해 DB 백업과 함께 보존되고, 공개 파일 URL 없이 기존 관리자 권한으로만 내려준다.
CREATE TABLE report_file
(
    report_file_id    BIGINT       NOT NULL AUTO_INCREMENT,
    target_year_month VARCHAR(7)   NOT NULL,
    kind              VARCHAR(20)  NOT NULL,
    file_name         VARCHAR(255) NOT NULL,
    content           LONGBLOB     NOT NULL,
    sha256            CHAR(64)     NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    employee_count    INT          NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (report_file_id),
    CONSTRAINT uk_report_file_year_month_kind UNIQUE (target_year_month, kind)
);
