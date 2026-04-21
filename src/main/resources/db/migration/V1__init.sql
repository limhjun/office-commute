CREATE TABLE team (
    team_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255),
    manager_name VARCHAR(255),
    member_count INT NOT NULL,
    annual_leave_criteria INT NOT NULL,
    PRIMARY KEY (team_id)
);

CREATE TABLE employee (
    employee_id BIGINT NOT NULL AUTO_INCREMENT,
    team_id BIGINT,
    name VARCHAR(255),
    role VARCHAR(255),
    birthday DATE,
    work_start_date DATE,
    employee_code VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    PRIMARY KEY (employee_id),
    CONSTRAINT uk_employee_code UNIQUE (employee_code),
    CONSTRAINT uk_employee_email UNIQUE (email),
    CONSTRAINT fk_employee_team FOREIGN KEY (team_id) REFERENCES team (team_id)
);

CREATE TABLE commute_history (
    commute_history_id BIGINT NOT NULL AUTO_INCREMENT,
    employee_id BIGINT,
    work_start_time DATETIME(6),
    work_end_time DATETIME(6),
    working_minutes BIGINT NOT NULL,
    using_day_off BIT(1) NOT NULL,
    work_date DATE NOT NULL,
    PRIMARY KEY (commute_history_id),
    CONSTRAINT uk_commute_history_employee_date UNIQUE (employee_id, work_date)
);

CREATE TABLE annual_leave (
    id BIGINT NOT NULL AUTO_INCREMENT,
    employee_id BIGINT,
    wanted_date DATE,
    PRIMARY KEY (id),
    CONSTRAINT uk_annual_leave_employee_date UNIQUE (employee_id, wanted_date)
);

CREATE TABLE holiday (
    id BIGINT NOT NULL AUTO_INCREMENT,
    year_value INT NOT NULL,
    month_value INT NOT NULL,
    holiday_date DATE NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE holiday_sync_status (
    id BIGINT NOT NULL AUTO_INCREMENT,
    year_value INT NOT NULL,
    month_value INT NOT NULL,
    last_successful_synced_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_holiday_sync_status_year_month UNIQUE (year_value, month_value)
);
