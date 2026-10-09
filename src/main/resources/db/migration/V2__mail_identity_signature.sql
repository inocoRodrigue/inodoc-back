ALTER TABLE users ADD COLUMN signature TEXT;

-- Legacy unowned or duplicate connections must be reconnected by their owner.
UPDATE mail_accounts SET active = false, created_by = NULL
WHERE created_by IS NULL OR created_by NOT IN (SELECT id FROM users);
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY created_by ORDER BY created_at, id) AS position
    FROM mail_accounts WHERE active = true AND created_by IS NOT NULL
)
UPDATE mail_accounts SET active = false WHERE id IN (SELECT id FROM ranked WHERE position > 1);

ALTER TABLE mail_accounts ADD CONSTRAINT fk_mail_owner
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE;
CREATE UNIQUE INDEX uq_mail_active_owner ON mail_accounts(created_by) WHERE active = true;
CREATE UNIQUE INDEX uq_mail_email_normalized ON mail_accounts(lower(email));
