ALTER TABLE users ADD COLUMN image_url VARCHAR(2048);

UPDATE users u
SET image_url = p.image_url
FROM profiles p
WHERE u.profile_id = p.id AND p.image_url IS NOT NULL;
