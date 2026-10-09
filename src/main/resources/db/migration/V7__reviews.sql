-- =====================================================================
-- Module "Entretiens" : documents de référence (consultatifs), modèles de
-- formulaire, campagnes annuelles / semi-annuelles et soumissions.
-- =====================================================================

CREATE TABLE reference_documents (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    category    VARCHAR(255),
    content     TEXT NOT NULL,
    created_by  UUID
);

CREATE TABLE review_forms (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    schema      TEXT NOT NULL,
    created_by  UUID
);

CREATE TABLE review_campaigns (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    title       VARCHAR(255) NOT NULL,
    type        VARCHAR(32) NOT NULL,
    form_id     UUID NOT NULL REFERENCES review_forms (id),
    start_date  DATE,
    end_date    DATE,
    status      VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_by  UUID
);

CREATE INDEX idx_review_campaigns_status ON review_campaigns (status);

CREATE TABLE review_submissions (
    id           UUID PRIMARY KEY,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    campaign_id  UUID NOT NULL REFERENCES review_campaigns (id) ON DELETE CASCADE,
    user_id      UUID NOT NULL,
    answers      TEXT NOT NULL,
    status       VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    submitted_at TIMESTAMPTZ,
    CONSTRAINT uk_review_submission_campaign_user UNIQUE (campaign_id, user_id)
);

CREATE INDEX idx_review_submissions_user ON review_submissions (user_id);
