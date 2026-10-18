CREATE TABLE cra_declarations (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    year          INTEGER NOT NULL CHECK (year BETWEEN 2000 AND 2100),
    month         INTEGER NOT NULL CHECK (month BETWEEN 1 AND 12),
    status        VARCHAR(20) NOT NULL,
    declared_at   TIMESTAMPTZ,
    validated_at  TIMESTAMPTZ,
    validated_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT uq_cra_declaration_user_month UNIQUE (user_id, year, month)
);

CREATE TABLE cra_day_entries (
    id              UUID PRIMARY KEY,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    declaration_id  UUID NOT NULL REFERENCES cra_declarations (id) ON DELETE CASCADE,
    work_date       DATE NOT NULL,
    project_id      UUID NOT NULL REFERENCES cra_projects (id),
    CONSTRAINT uq_cra_day_declaration_date UNIQUE (declaration_id, work_date)
);

CREATE INDEX idx_cra_declarations_year_status ON cra_declarations (year, status);