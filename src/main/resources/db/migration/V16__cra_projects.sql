CREATE TABLE cra_projects (
    id              UUID PRIMARY KEY,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    code            VARCHAR(100) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    type            VARCHAR(40) NOT NULL,
    system_defined  BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE cra_project_collaborators (
    project_id UUID NOT NULL REFERENCES cra_projects (id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (project_id, user_id)
);

CREATE INDEX idx_cra_project_collaborators_user
    ON cra_project_collaborators (user_id);