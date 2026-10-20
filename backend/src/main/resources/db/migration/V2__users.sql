CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(20)  NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    weight_kg     NUMERIC(5,1),
    goal_type     VARCHAR(20)  NOT NULL DEFAULT 'MINUTES',
    goal_target   INT          NOT NULL DEFAULT 150,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX users_username_lower ON users (LOWER(username));
CREATE UNIQUE INDEX users_email ON users (email);
