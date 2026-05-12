-- 시스템 부트스트랩 admin 계정과 관리팀을 시드한다.
-- dev 프로파일에서는 data.sql 이 같은 역할을 하지만, mysql/prod 프로파일은
-- `sql.init.mode: never` 이므로 Flyway 가 유일한 시드 통로다.
-- 비밀번호 평문: admin1234 (BCryptPasswordEncoder strength=10).
INSERT INTO employee (name, role, birthday, work_start_date, employee_code, email, password, timezone)
VALUES ('관리자', 'MANAGER', '1990-01-01', '2024-01-01', 'ADMIN001', 'admin@company.com',
        '$2a$10$jg1.5WoxGYRAvXMnQbjuzO00fqODW80lysuhA0an2vD/VqgHY6MDm', 'Asia/Seoul');

INSERT INTO team (name, manager_name, annual_leave_criteria)
VALUES ('관리팀', '관리자', 15);
