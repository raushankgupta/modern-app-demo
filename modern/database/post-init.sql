-- Modern-only post-init step (Story D2). Run by init.sh after the legacy seed.
-- Replaces the plain-text demo password with a BCrypt hash. init.sh computes the
-- hash at run time and substitutes the placeholder below, so no hash is committed.
-- The legacy seed-data.sql is NOT edited.
UPDATE EMPLOYEES SET PASSWORD = '__ADMIN_PASSWORD_HASH__' WHERE USERNAME = 'admin';
