DELETE FROM employee WHERE employee_code = 'ADMIN001';
DELETE FROM team WHERE name = '관리팀';

INSERT INTO employee (name, role, birthday, work_start_date, employee_code, pin)
VALUES ('관리자', 'MANAGER', '1990-01-01', '2024-01-01', 'ADMIN001', '1234');

INSERT INTO team (name, manager_name, member_count, annual_leave_criteria)
VALUES ('관리팀', '관리자', 1, 15);
