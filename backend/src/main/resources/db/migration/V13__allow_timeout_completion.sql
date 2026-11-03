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
                        termination_reason IN (
                            'CHECKMATE',
                            'RESIGNATION',
                            'TIMEOUT'
                        )
                        AND result IN ('WHITE_WIN', 'BLACK_WIN')
                    )
                    OR
                    (
                        termination_reason IN (
                            'STALEMATE',
                            'DRAW_AGREEMENT'
                        )
                        AND result = 'DRAW'
                    )
                )
            )
        );