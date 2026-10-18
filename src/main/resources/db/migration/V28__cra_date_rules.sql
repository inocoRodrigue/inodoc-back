CREATE TABLE cra_date_rules (
    policy_id INTEGER NOT NULL REFERENCES cra_policy(id) ON DELETE CASCADE,
    rule_date DATE NOT NULL,
    rule_type VARCHAR(30) NOT NULL CHECK (rule_type IN ('ASSIGNED_ACTIVITY', 'PUBLIC_HOLIDAY', 'NON_WORKING')),
    label VARCHAR(255),
    project_id UUID REFERENCES cra_projects(id),
    PRIMARY KEY (policy_id, rule_date),
    CHECK ((rule_type = 'ASSIGNED_ACTIVITY' AND project_id IS NOT NULL)
        OR (rule_type <> 'ASSIGNED_ACTIVITY' AND project_id IS NULL))
);