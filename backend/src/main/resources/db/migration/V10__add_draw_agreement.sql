ALTER TABLE games
    ADD COLUMN draw_offered_by_player_id BIGINT;

-- A pending offer belongs to a participant in an active game.
-- Participant IDs already reference Players through the existing columns.
ALTER TABLE games
    ADD CONSTRAINT ck_games_draw_offer
        CHECK (
            draw_offered_by_player_id IS NULL
            OR (
                status = 'IN_PROGRESS'
                AND draw_offered_by_player_id IN (
                    white_player_id,
                    black_player_id
                )
            )
        );

ALTER TABLE games
    DROP CONSTRAINT ck_games_completion;

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
                        termination_reason IN ('CHECKMATE', 'RESIGNATION')
                        AND result IN ('WHITE_WIN', 'BLACK_WIN')
                    )
                    OR
                    (
                        termination_reason IN ('STALEMATE', 'DRAW_AGREEMENT')
                        AND result = 'DRAW'
                    )
                )
            )
        );