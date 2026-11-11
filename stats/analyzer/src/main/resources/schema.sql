CREATE TABLE IF NOT EXISTS user_event_weights (
    user_id    BIGINT           NOT NULL,
    event_id   BIGINT           NOT NULL,
    weight     DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMP        NOT NULL,
    PRIMARY KEY (user_id, event_id)
);

CREATE INDEX IF NOT EXISTS idx_user_event_weights_event ON user_event_weights (event_id);

CREATE TABLE IF NOT EXISTS event_similarities (
    event_a    BIGINT           NOT NULL,
    event_b    BIGINT           NOT NULL,
    score      DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMP        NOT NULL,
    PRIMARY KEY (event_a, event_b)
);

CREATE INDEX IF NOT EXISTS idx_event_similarities_b ON event_similarities (event_b);
