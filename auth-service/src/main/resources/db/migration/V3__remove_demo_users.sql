-- V1 seeded two demo accounts with a published password, in every environment. Remove them everywhere;
-- the local profile seeds them again from db/dev (R__demo_users.sql). A no-op where they don't exist.
-- refresh_tokens.user_id cascades on delete, but say it explicitly.
DELETE FROM refresh_tokens
WHERE user_id IN (SELECT id FROM users WHERE email IN ('daniel@example.com', 'admin@example.com'));

DELETE FROM users
WHERE email IN ('daniel@example.com', 'admin@example.com');
