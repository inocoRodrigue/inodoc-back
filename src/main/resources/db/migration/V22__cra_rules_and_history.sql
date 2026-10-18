ALTER TABLE cra_declarations ADD COLUMN editable_until DATE;
ALTER TABLE cra_declarations ADD COLUMN rejection_reason TEXT;
CREATE TABLE cra_policy (id INTEGER PRIMARY KEY, deadline_day INTEGER NOT NULL DEFAULT 5 CHECK (deadline_day BETWEEN 1 AND 28), allow_self_approval BOOLEAN NOT NULL DEFAULT FALSE);
CREATE TABLE cra_holidays (policy_id INTEGER NOT NULL REFERENCES cra_policy(id) ON DELETE CASCADE, holiday_date DATE NOT NULL, PRIMARY KEY(policy_id, holiday_date));
CREATE TABLE cra_work_schedules (user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, morning_mask INTEGER NOT NULL DEFAULT 31 CHECK (morning_mask BETWEEN 0 AND 127), afternoon_mask INTEGER NOT NULL DEFAULT 31 CHECK (afternoon_mask BETWEEN 0 AND 127), start_date DATE, end_date DATE, CHECK (end_date IS NULL OR start_date IS NULL OR start_date <= end_date));
CREATE TABLE cra_periods (id VARCHAR(7) PRIMARY KEY, closed BOOLEAN NOT NULL DEFAULT FALSE, open_until DATE, reason VARCHAR(1000));
CREATE TABLE cra_audit_events (id UUID PRIMARY KEY, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, declaration_id UUID REFERENCES cra_declarations(id) ON DELETE SET NULL, actor_id UUID, actor_name VARCHAR(255), action VARCHAR(255) NOT NULL, reason TEXT, before_state TEXT, after_state TEXT);
CREATE INDEX idx_cra_audit_declaration_created ON cra_audit_events(declaration_id, created_at DESC);