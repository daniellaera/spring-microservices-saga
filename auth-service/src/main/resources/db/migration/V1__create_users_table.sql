CREATE TABLE users
(
    id         BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(255),
    last_name  VARCHAR(255),
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(50)  NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uc_users_email UNIQUE (email)
);

-- Demo credentials (dev/seed only — never use in production):
-- email: daniel@example.com | password: password123
-- email: admin@example.com  | password: password123
INSERT INTO users (first_name, last_name, email, password, role)
VALUES ('Daniel', 'Laera', 'daniel@example.com', '$2a$10$0EIYtB31WcPHZqresUb8de7u6ICMWFiC4GRmXaQUud3POlJ9oegIO',
        'USER'),
       ('Admin', 'User', 'admin@example.com', '$2a$10$0EIYtB31WcPHZqresUb8de7u6ICMWFiC4GRmXaQUud3POlJ9oegIO', 'ADMIN');