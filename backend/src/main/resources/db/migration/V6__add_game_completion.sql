ALTER TABLE games
    ADD COLUMN result VARCHAR(30),
    ADD COLUMN termination_reason VARCHAR(30),
    ADD COLUMN finished_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE games
    DROP CONSTRAINT ck_games_status;

ALTER TABLE games
    ADD CONSTRAINT ck_games_status
        CHECK (status IN ('IN_PROGRESS', 'FINISHED'));

ALTER TABLE games
    ADD CONSTRAINT ck_games_completion
        CHECK (
            (
                status = 'IN_PROGRESS'
                AND result IS NULL
                AND termination_reason IS NULL
                AND finished_at IS NULL
            )
            OR
            (
                status = 'FINISHED'
                AND result IS NOT NULL
                AND termination_reason IS NOT NULL
                AND finished_at IS NOT NULL
                AND (
                    (
                        termination_reason = 'CHECKMATE'
                        AND result IN ('WHITE_WIN', 'BLACK_WIN')
                    )
                    OR
                    (
                        termination_reason = 'STALEMATE'
                        AND result = 'DRAW'
                    )
                )
            )
        );
-- The constraint permits exactly the supported combinations:
--  - An in-progress game has no completion information.
--  - Checkmate requires a winner.
--  - Stalemate requires a draw.
--  - Every finished game has a finish timestamp.

-- The explicit IS NOT NULL checks matter because SQL CHECK constraints also accept expressions that evaluate to UNKNOWN