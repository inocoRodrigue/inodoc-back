CREATE TABLE career_entry_shares (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    career_entry_id UUID NOT NULL REFERENCES career_entries (id) ON DELETE CASCADE,
    manager_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_career_entry_share_manager UNIQUE (career_entry_id, manager_user_id)
);

CREATE INDEX idx_career_entry_shares_manager
    ON career_entry_shares (manager_user_id, created_at DESC);

CREATE TABLE review_submission_shares (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    submission_id UUID NOT NULL REFERENCES review_submissions (id) ON DELETE CASCADE,
    manager_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_review_submission_share_manager UNIQUE (submission_id, manager_user_id)
);

CREATE INDEX idx_review_submission_shares_manager
    ON review_submission_shares (manager_user_id, created_at DESC);