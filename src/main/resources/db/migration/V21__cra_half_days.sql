ALTER TABLE cra_day_entries ADD COLUMN day_part VARCHAR(20) NOT NULL DEFAULT 'FULL_DAY';
ALTER TABLE cra_day_entries ADD COLUMN afternoon_project_id UUID REFERENCES cra_projects(id);
ALTER TABLE cra_day_entries ADD COLUMN afternoon_work_location VARCHAR(20);
ALTER TABLE cra_day_entries ADD CONSTRAINT ck_cra_day_part CHECK (day_part IN ('FULL_DAY', 'MORNING', 'AFTERNOON'));
ALTER TABLE cra_day_entries ADD CONSTRAINT ck_cra_afternoon CHECK (
    (day_part = 'FULL_DAY' OR afternoon_project_id IS NULL)
    AND (afternoon_work_location IS NULL OR afternoon_project_id IS NOT NULL)
);