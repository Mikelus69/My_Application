CREATE TABLE achievements (
    code        VARCHAR(30)  PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(200) NOT NULL,
    emoji       VARCHAR(10)  NOT NULL,
    requirement VARCHAR(30)  NOT NULL,
    threshold   INT          NOT NULL,
    sport_code  VARCHAR(30)  REFERENCES sport_types (code)
);

CREATE TABLE user_achievements (
    user_id          BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    achievement_code VARCHAR(30) NOT NULL REFERENCES achievements (code),
    unlocked_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, achievement_code)
);

INSERT INTO achievements (code, name, description, emoji, requirement, threshold, sport_code) VALUES
    ('FIRST_ACTIVITY', 'Erste Schritte',   'Erste Aktivität eingetragen', '🥉', 'TOTAL_ACTIVITIES', 1,    NULL),
    ('KCAL_1000',      'Feuer und Flamme', '1000 kcal verbrannt',         '🔥', 'TOTAL_KCAL',       1000, NULL),
    ('RUNNER_10',      'Läufer',           '10 Laufaktivitäten',          '🏃', 'SPORT_ACTIVITIES', 10,   'RUNNING'),
    ('MINUTES_500',    'Ausdauer',         '500 Minuten Sport',           '⏱️', 'TOTAL_MINUTES',    500,  NULL),
    ('STREAK_7',       'Sportwoche',       '7 Tage in Folge aktiv',       '🌟', 'STREAK_DAYS',      7,    NULL),
    ('MARATHON',       'Marathon',         '42 Stunden Sport',            '🏆', 'TOTAL_MINUTES',    2520, NULL);
