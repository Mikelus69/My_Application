CREATE TABLE activity_photos (
    activity_id BIGINT PRIMARY KEY REFERENCES activities (id) ON DELETE CASCADE,
    image       BYTEA  NOT NULL
);

CREATE SEQUENCE photo_versions;

ALTER TABLE activities ADD COLUMN photo_version BIGINT;
