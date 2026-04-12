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

INSERT INTO users (first_name, last_name, email, password, role)
VALUES ('Daniel', 'Laera', 'daniel@example.com', '$2a$10$vijzIeofJKEIBByFk4Gov.3RDmZTtQbChx51heq//phWExKRO6iMq',
        'USER'),
       ('Admin', 'User', 'admin@example.com', '$2a$10$vijzIeofJKEIBByFk4Gov.3RDmZTtQbChx51heq//phWExKRO6iMq', 'ADMIN');