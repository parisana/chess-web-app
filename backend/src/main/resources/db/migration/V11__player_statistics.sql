--We’ll keep statistics in a separate PlayerStatistics entity owned by player. This keeps rating updates separate from player updates e.g username/email updates.
CREATE TABLE player_statistics (
    player_id BIGINT PRIMARY KEY,
    rating INTEGER NOT NULL DEFAULT 1200,
    wins BIGINT NOT NULL DEFAULT 0,
    draws BIGINT NOT NULL DEFAULT 0,
    losses BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_player_statistics_player
        FOREIGN KEY (player_id)
        REFERENCES players(id),

    CONSTRAINT ck_player_statistics_counts
        CHECK (wins >= 0 AND draws >= 0 AND losses >= 0),

    CONSTRAINT ck_player_statistics_version
        CHECK (version >= 0)
);

-- Existing Players start with the same initial rating and zero statistics.
INSERT INTO player_statistics (player_id)
SELECT id
FROM players;

CREATE TABLE player_statistics_processed_games (
    game_id BIGINT PRIMARY KEY,
    statistics_applied BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_player_statistics_processed_game
        FOREIGN KEY (game_id)
        REFERENCES games(id)
);

-- Historical completions are deliberately excluded.
-- Recording them also prevents a later repeated event from counting them.
INSERT INTO player_statistics_processed_games (
    game_id,
    statistics_applied
)
SELECT id, FALSE
FROM games
WHERE status = 'FINISHED';