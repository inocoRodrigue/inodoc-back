DROP TABLE IF EXISTS cra_periods;
ALTER TABLE cra_policy DROP COLUMN IF EXISTS deadline_day;
ALTER TABLE cra_declarations DROP COLUMN IF EXISTS editable_until;