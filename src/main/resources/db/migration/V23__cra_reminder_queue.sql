CREATE TABLE cra_reminders (
    id UUID PRIMARY KEY, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    requested_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mail_account_id UUID NOT NULL REFERENCES mail_accounts(id) ON DELETE CASCADE,
    period_year INTEGER NOT NULL, period_month INTEGER NOT NULL,
    queued_date DATE NOT NULL, recipient VARCHAR(255) NOT NULL, state VARCHAR(255) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ, sent_at TIMESTAMPTZ, error TEXT,
    UNIQUE(user_id, period_year, period_month, queued_date)
);
CREATE INDEX idx_cra_reminders_dispatch ON cra_reminders(state, next_attempt_at);