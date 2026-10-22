-- This table has no entity behavior or callbacks, so a small JDBC repository is sufficient when required.
CREATE TABLE active_game_players (
    player_id BIGINT PRIMARY KEY,
    game_id BIGINT NOT NULL,

    CONSTRAINT fk_active_game_players_player
        FOREIGN KEY (player_id)
        REFERENCES players(id),

    CONSTRAINT fk_active_game_players_game
        FOREIGN KEY (game_id)
        REFERENCES games(id)
);

CREATE INDEX idx_active_game_players_game
    ON active_game_players (game_id);

-- The backfill also covers games already created before V5. If existing data contains a player in multiple active games, the migration will fail rather than silently choose one; that data would need reconciliation.
INSERT INTO active_game_players (player_id, game_id)
SELECT white_player_id, id
FROM games
WHERE status = 'IN_PROGRESS'
UNION ALL
SELECT black_player_id, id
FROM games
WHERE status = 'IN_PROGRESS';