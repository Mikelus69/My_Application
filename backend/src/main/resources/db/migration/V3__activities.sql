CREATE TABLE activities (
    id            BIGSERIAL        PRIMARY KEY,
    user_id       BIGINT           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    sport_code    VARCHAR(30)      NOT NULL REFERENCES sport_types (code),
    started_at    TIMESTAMPTZ      NOT NULL,
    duration_min  INT              NOT NULL,
    calories_kcal INT              NOT NULL,
    points        INT              NOT NULL,
    goal_bonus    BOOLEAN          NOT NULL DEFAULT FALSE,
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    description   VARCHAR(500),
    visibility    VARCHAR(10)      NOT NULL DEFAULT 'PRIVATE',
    created_at    TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX activities_user_started ON activities (user_id, started_at DESC);
