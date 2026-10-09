-- =====================================================================
-- InoDoc : schema initial (application mono-societe)
-- =====================================================================

CREATE TABLE permissions (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    code        VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE profiles (
    id                  UUID PRIMARY KEY,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    first_name          VARCHAR(255) NOT NULL,
    last_name           VARCHAR(255) NOT NULL,
    email               VARCHAR(255),
    phone               VARCHAR(255),
    address             VARCHAR(255),
    location            VARCHAR(255),
    availability_status VARCHAR(255),
    available_from      DATE,
    status              VARCHAR(255) NOT NULL,
    daily_rate          INTEGER
);

CREATE TABLE roles (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(255) NOT NULL,
    last_name     VARCHAR(255) NOT NULL,
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    profile_id    UUID REFERENCES profiles (id) ON DELETE SET NULL
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE skill_categories (
    id         UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    name       VARCHAR(255) NOT NULL UNIQUE
);

-- =====================================================================
-- Dossiers de competences versionnes
-- Un profil (personne) possede 1..N dossiers ; l'un d'eux est "master"
-- (version de reference definie par l'ingenieur d'affaires).
-- =====================================================================
CREATE TABLE competence_dossiers (
    id                  UUID PRIMARY KEY,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    profile_id          UUID NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    label               VARCHAR(255) NOT NULL,
    master              BOOLEAN NOT NULL DEFAULT FALSE,
    headline            VARCHAR(255),
    years_of_experience INTEGER,
    expertise_domain    VARCHAR(255),
    summary             TEXT
);
CREATE INDEX idx_dossiers_profile ON competence_dossiers (profile_id);

CREATE TABLE skill_groups (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    dossier_id  UUID NOT NULL REFERENCES competence_dossiers (id) ON DELETE CASCADE,
    category    VARCHAR(255) NOT NULL,
    content     TEXT,
    position    INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_skill_groups_dossier ON skill_groups (dossier_id);

CREATE TABLE experiences (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    dossier_id  UUID NOT NULL REFERENCES competence_dossiers (id) ON DELETE CASCADE,
    company     VARCHAR(255) NOT NULL,
    start_date  DATE,
    end_date    DATE,
    role_title  VARCHAR(255),
    context     TEXT,
    position    INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_experiences_dossier ON experiences (dossier_id);

CREATE TABLE experience_projects (
    id           UUID PRIMARY KEY,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    experience_id UUID NOT NULL REFERENCES experiences (id) ON DELETE CASCADE,
    name         VARCHAR(255),
    context      TEXT,
    team         VARCHAR(255),
    methodology  VARCHAR(255),
    tasks        TEXT,
    technologies TEXT,
    position     INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_experience_projects_exp ON experience_projects (experience_id);

CREATE TABLE certifications (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    dossier_id  UUID NOT NULL REFERENCES competence_dossiers (id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    issuer      VARCHAR(255),
    year        VARCHAR(255),
    link        VARCHAR(1024),
    image_url   TEXT,
    position    INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_certifications_dossier ON certifications (dossier_id);

CREATE TABLE educations (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    dossier_id  UUID NOT NULL REFERENCES competence_dossiers (id) ON DELETE CASCADE,
    school      VARCHAR(255) NOT NULL,
    degree      VARCHAR(255),
    year        VARCHAR(255),
    link        VARCHAR(1024),
    image_url   TEXT,
    position    INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_educations_dossier ON educations (dossier_id);

CREATE TABLE language_skills (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    dossier_id  UUID NOT NULL REFERENCES competence_dossiers (id) ON DELETE CASCADE,
    language    VARCHAR(255) NOT NULL,
    level       VARCHAR(255),
    position    INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_language_skills_dossier ON language_skills (dossier_id);

-- References professionnelles structurees (contact joignable)
CREATE TABLE dossier_references (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    dossier_id  UUID NOT NULL REFERENCES competence_dossiers (id) ON DELETE CASCADE,
    name        VARCHAR(255),
    email       VARCHAR(255),
    phone       VARCHAR(255),
    description TEXT,
    link        VARCHAR(1024),
    position    INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_dossier_references_dossier ON dossier_references (dossier_id);

-- Resultats de tests techniques (note, lien, image)
CREATE TABLE test_results (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    dossier_id  UUID NOT NULL REFERENCES competence_dossiers (id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    score       VARCHAR(255),
    description TEXT,
    link        VARCHAR(1024),
    image_url   TEXT,
    position    INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_test_results_dossier ON test_results (dossier_id);

-- Catalogue de competences techniques propose en autocompletion
CREATE TABLE tech_skills (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    category    VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE company_settings (
    id              UUID PRIMARY KEY,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    address         VARCHAR(512),
    phone           VARCHAR(255),
    email           VARCHAR(255),
    email_signature TEXT
);

CREATE TABLE shared_links (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    token         VARCHAR(255) NOT NULL UNIQUE,
    dossier_id    UUID NOT NULL REFERENCES competence_dossiers (id) ON DELETE CASCADE,
    type          VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    expires_at    TIMESTAMPTZ,
    max_opens     INTEGER,
    open_count    INTEGER NOT NULL DEFAULT 0,
    revoked       BOOLEAN NOT NULL DEFAULT FALSE,
    created_by    UUID
);

-- =====================================================================
-- Comptes Google connectes via OAuth2 permettant l'envoi de mails "au nom de
-- inoDoc" avec l'API Gmail. Le refresh token est chiffre au repos (AES-GCM).
-- =====================================================================
CREATE TABLE mail_accounts (
    id                      UUID PRIMARY KEY,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    email                   VARCHAR(255) NOT NULL UNIQUE,
    display_name            VARCHAR(255),
    refresh_token_encrypted TEXT NOT NULL,
    created_by              UUID,
    active                  BOOLEAN NOT NULL DEFAULT TRUE
);
