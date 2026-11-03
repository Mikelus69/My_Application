-- Diese Sportarten stehen in der Cloud schon, weil sie dort direkt eingetragen wurden. ON CONFLICT überspringt sie dort.
INSERT INTO sport_types (code, name, emoji, met) VALUES
    ('BASEBALL',     'Baseball',    '⚾', 5.0),
    ('BOXING',       'Boxen',       '🥊', 6.6),
    ('CLIMBING',     'Klettern',    '🧗', 7.3),
    ('DIVING',       'Tauchen',     '🤿', 5.0),
    ('FISHING',      'Angeln',      '🎣', 3.7),
    ('HORSE_RIDING', 'Reiten',      '🏇', 5.5),
    ('MARTIAL_ARTS', 'Kampfsport',  '🥋', 10.0),
    ('ROWING',       'Rudern',      '🚣', 5.9),
    ('SKIING',       'Skifahren',   '⛷️', 7.8),
    ('SURFING',      'Surfen',      '🏄', 5.0),
    ('TABLE_TENNIS', 'Tischtennis', '🏓', 4.0),
    ('TENNIS',       'Tennis',      '🎾', 5.0),
    ('VOLLEYBALL',   'Volleyball',  '🏐', 6.5),
    ('YOGA',         'Yoga',        '🧘', 2.5)
ON CONFLICT (code) DO NOTHING;
