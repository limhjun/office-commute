-- Employee 컬럼에 NOT NULL 제약 추가.
-- 도메인 검증과 스키마 보장을 일치시킨다 (`ddl-auto: validate` 정합).
-- 운영 적용 전: name/role/birthday/work_start_date에 NULL row가 없는지 점검 필요.
ALTER TABLE employee
    MODIFY COLUMN name VARCHAR(255) NOT NULL,
    MODIFY COLUMN role VARCHAR(255) NOT NULL,
    MODIFY COLUMN birthday DATE NOT NULL,
    MODIFY COLUMN work_start_date DATE NOT NULL;

-- Team.member_count 컬럼 제거.
-- 멤버 수는 employee.team_id의 COUNT로 파생한다 (옵션 A — denormalized counter
-- 제거로 drift / lost-update race / 도메인 결합 동시 해소).
ALTER TABLE team
    DROP COLUMN member_count;
