-- 퇴근 시각 정정 (Issue #52).

-- 근태 원본의 변경 검출. 일반 퇴근·정정 승인의 조건부 UPDATE 가 직접 1 올린다
-- (JPQL bulk UPDATE 는 @Version 을 자동 증가시키지 않는다).
ALTER TABLE commute_history
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- MANAGER·COMMUTE_APPROVER 의 정정 요청을 처리할 지정 승인자. MEMBER 는 NULL(모든 MANAGER 가 처리).
ALTER TABLE employee
    ADD COLUMN correction_approver_id BIGINT NULL;

ALTER TABLE employee
    ADD CONSTRAINT fk_employee_correction_approver
        FOREIGN KEY (correction_approver_id) REFERENCES employee (employee_id);

-- 정정 요청. 요청 행이 이력을 겸한다 — PENDING 이후 한 번만 전이하고, 종착 상태 행은 바뀌지 않는다.
-- previous_* 는 신청 당시 원본 스냅샷이다. 승인은 commute_version 이 같을 때만 적용되므로
-- 승인 직전 값과도 같다. 현재 근태 값으로 이력을 재구성하지 않는다.
--
-- pending_commute_history_id: PENDING 동안만 commute_history_id 를 담고 처리·취소 시 NULL 로 비운다.
-- UNIQUE 는 NULL 을 중복으로 보지 않으므로 "기록당 대기 요청 1건"을 DB 가 보장하면서
-- 처리 후 재신청을 허용한다 (MySQL 에 부분 인덱스가 없어서 쓰는 방식).
CREATE TABLE commute_correction_request
(
    correction_request_id      BIGINT       NOT NULL AUTO_INCREMENT,
    version                    BIGINT       NOT NULL,
    commute_history_id         BIGINT       NOT NULL,
    pending_commute_history_id BIGINT       NULL,
    requester_id               BIGINT       NOT NULL,
    requester_role             VARCHAR(20)  NOT NULL,
    assigned_approver_id       BIGINT       NULL,
    commute_version            BIGINT       NOT NULL,
    work_date                  DATE         NOT NULL,
    work_zone                  VARCHAR(64)  NOT NULL,
    work_start_time            DATETIME(6)  NOT NULL,
    previous_work_end_time     DATETIME(6)  NULL,
    previous_working_minutes   BIGINT       NOT NULL,
    requested_work_end_time    DATETIME(6)  NOT NULL,
    requested_working_minutes  BIGINT       NOT NULL,
    reason                     VARCHAR(500) NOT NULL,
    status                     VARCHAR(20)  NOT NULL, -- PENDING | APPROVED | REJECTED | CANCELLED
    requested_at               DATETIME(6)  NOT NULL,
    processed_by_id            BIGINT       NULL,
    processed_at               DATETIME(6)  NULL,
    review_comment             VARCHAR(500) NULL,
    PRIMARY KEY (correction_request_id),
    CONSTRAINT uk_correction_request_pending UNIQUE (pending_commute_history_id),
    CONSTRAINT fk_correction_request_commute FOREIGN KEY (commute_history_id) REFERENCES commute_history (commute_history_id),
    CONSTRAINT fk_correction_request_requester FOREIGN KEY (requester_id) REFERENCES employee (employee_id),
    CONSTRAINT fk_correction_request_approver FOREIGN KEY (assigned_approver_id) REFERENCES employee (employee_id),
    CONSTRAINT fk_correction_request_processed_by FOREIGN KEY (processed_by_id) REFERENCES employee (employee_id)
);

CREATE INDEX idx_correction_request_requester ON commute_correction_request (requester_id);
CREATE INDEX idx_correction_request_approver ON commute_correction_request (assigned_approver_id);
CREATE INDEX idx_correction_request_status_work_date ON commute_correction_request (status, work_date);
