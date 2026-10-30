ALTER TABLE game_moves
    ADD COLUMN san VARCHAR(32);

ALTER TABLE game_moves
    ADD CONSTRAINT ck_game_moves_san_nonblank
        CHECK (san IS NULL OR length(btrim(san)) > 0);

COMMENT ON COLUMN game_moves.san IS
    'Standard algebraic notation. NULL for entries recorded before SAN support.';