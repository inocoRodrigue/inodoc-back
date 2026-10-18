UPDATE cra_projects
SET type = 'ACCESS_ALL'
WHERE type NOT IN ('CLIENT', 'ACCESS_ALL');