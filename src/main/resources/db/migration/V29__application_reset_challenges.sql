CREATE TABLE application_reset_challenges (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    challenge_id UUID NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5)
);