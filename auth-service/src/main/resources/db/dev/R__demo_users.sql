-- Demo accounts for local development only: this location is enabled by the local profile
-- (application-local.yml), never in production. Repeatable, so it runs after every versioned migration,
-- including V3, which removes these accounts everywhere.
-- email: daniel@example.com | password: password123
-- email: admin@example.com  | password: password123
INSERT INTO users (first_name, last_name, email, password, role)
VALUES ('Daniel', 'Laera', 'daniel@example.com', '$2a$10$0EIYtB31WcPHZqresUb8de7u6ICMWFiC4GRmXaQUud3POlJ9oegIO',
        'USER'),
       ('Admin', 'User', 'admin@example.com', '$2a$10$0EIYtB31WcPHZqresUb8de7u6ICMWFiC4GRmXaQUud3POlJ9oegIO', 'ADMIN')
ON CONFLICT (email) DO NOTHING;
