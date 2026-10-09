ALTER TABLE company_settings
    ADD COLUMN stale_dossier_months INTEGER NOT NULL DEFAULT 12;