ALTER TABLE employee
    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul';

ALTER TABLE commute_history
    ADD COLUMN work_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul';
