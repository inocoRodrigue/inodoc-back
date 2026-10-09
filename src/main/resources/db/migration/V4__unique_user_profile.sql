CREATE UNIQUE INDEX uq_users_profile ON users(profile_id) WHERE profile_id IS NOT NULL;
