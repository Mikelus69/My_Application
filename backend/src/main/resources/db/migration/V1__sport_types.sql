CREATE TABLE sport_types (
    code  VARCHAR(30)  PRIMARY KEY,
    name  VARCHAR(50)  NOT NULL,
    emoji VARCHAR(10)  NOT NULL,
    met   NUMERIC(4,1) NOT NULL
);

INSERT INTO sport_types (code, name, emoji, met) VALUES
    ('WALKING',    'Gehen',         '🚶', 3.5),
    ('RUNNING',    'Joggen',        '🏃', 8.0),
    ('CYCLING',    'Radfahren',     '🚴', 7.0),
    ('SWIMMING',   'Schwimmen',     '🏊', 6.0),
    ('FOOTBALL',   'Fußball',       '⚽', 7.0),
    ('BASKETBALL', 'Basketball',    '🏀', 6.5),
    ('HIKING',     'Wandern',       '🥾', 5.0),
    ('STRENGTH',   'Krafttraining', '🏋️', 5.0);
