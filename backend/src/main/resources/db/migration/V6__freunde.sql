CREATE TABLE friendships (
    requester_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    addressee_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status       VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (requester_id, addressee_id),
    CHECK (requester_id <> addressee_id)
);

CREATE UNIQUE INDEX friendships_pair ON friendships (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id));
CREATE INDEX friendships_addressee ON friendships (addressee_id);
