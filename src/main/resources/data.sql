DELETE FROM employee WHERE employee_code = 'ADMIN001';
DELETE FROM team WHERE name = '관리팀';

-- 관리자 비밀번호(평문): admin1234 (BCryptPasswordEncoder strength=10)
INSERT INTO employee (name, role, birthday, work_start_date, employee_code, email, password, timezone)
VALUES ('관리자', 'MANAGER', '1990-01-01', '2024-01-01', 'ADMIN001', 'admin@company.com',
        '$2a$10$jg1.5WoxGYRAvXMnQbjuzO00fqODW80lysuhA0an2vD/VqgHY6MDm', 'Asia/Seoul');

INSERT INTO team (name, manager_name, annual_leave_criteria)
VALUES ('관리팀', '관리자', 15);

-- 상위 승인자(COMMUTE_APPROVER) 개발용 계정. 비밀번호(평문): admin1234
-- 관리자 ↔ 상위 승인자를 서로의 정정 승인 담당자로 지정해 두 역할의 정정 흐름을 바로 확인할 수 있게 한다.
INSERT INTO employee (name, role, birthday, work_start_date, employee_code, email, password, timezone)
VALUES ('상위승인자', 'COMMUTE_APPROVER', '1985-01-01', '2024-01-01', 'APPROVER01', 'approver@company.com',
        '$2a$10$jg1.5WoxGYRAvXMnQbjuzO00fqODW80lysuhA0an2vD/VqgHY6MDm', 'Asia/Seoul');

UPDATE employee
SET correction_approver_id = (SELECT e.employee_id FROM (SELECT employee_id FROM employee WHERE employee_code = 'APPROVER01') e)
WHERE employee_code = 'ADMIN001';

UPDATE employee
SET correction_approver_id = (SELECT e.employee_id FROM (SELECT employee_id FROM employee WHERE employee_code = 'ADMIN001') e)
WHERE employee_code = 'APPROVER01';
