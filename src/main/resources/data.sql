DELETE FROM employee WHERE employee_code = 'ADMIN001';
DELETE FROM team WHERE name = '관리팀';

-- 관리자 비밀번호(평문): admin1234 (BCryptPasswordEncoder strength=10)
INSERT INTO employee (name, role, birthday, work_start_date, employee_code, email, password)
VALUES ('관리자', 'MANAGER', '1990-01-01', '2024-01-01', 'ADMIN001', 'admin@company.com',
        '$2a$10$jg1.5WoxGYRAvXMnQbjuzO00fqODW80lysuhA0an2vD/VqgHY6MDm');

INSERT INTO team (name, manager_name, member_count, annual_leave_criteria)
VALUES ('관리팀', '관리자', 1, 15);
