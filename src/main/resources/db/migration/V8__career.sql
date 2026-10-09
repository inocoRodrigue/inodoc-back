-- =====================================================================
-- Module "Carnet de carrière" : un modèle partagé (défini par les
-- ingénieurs d'affaires) et les entrées personnelles de chaque employé.
-- =====================================================================

CREATE TABLE career_templates (
    id           UUID PRIMARY KEY,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    entry_schema TEXT NOT NULL,
    created_by   UUID
);

CREATE TABLE career_entries (
    id         UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    user_id    UUID NOT NULL,
    title      VARCHAR(255) NOT NULL,
    entry_date DATE,
    data       TEXT NOT NULL
);

CREATE INDEX idx_career_entries_user ON career_entries (user_id);
