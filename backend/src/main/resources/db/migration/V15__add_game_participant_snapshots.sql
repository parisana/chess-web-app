ALTER TABLE games
    ADD COLUMN white_username_at_start VARCHAR(30),
    ADD COLUMN black_username_at_start VARCHAR(30),
    ADD COLUMN white_rating_at_start INTEGER,
    ADD COLUMN black_rating_at_start INTEGER;

-- Historical starting ratings and usernames cannot be reconstructed.
-- Existing games retain an entirely absent snapshot.
ALTER TABLE games
    ADD CONSTRAINT ck_games_participant_snapshots
    CHECK (
        (
            white_username_at_start IS NULL
            AND black_username_at_start IS NULL
            AND white_rating_at_start IS NULL
            AND black_rating_at_start IS NULL
        )
        OR
        (
            white_username_at_start IS NOT NULL
            AND black_username_at_start IS NOT NULL
            AND white_rating_at_start IS NOT NULL
            AND black_rating_at_start IS NOT NULL
            AND btrim(white_username_at_start) <> ''
            AND btrim(black_username_at_start) <> ''
        )
    );