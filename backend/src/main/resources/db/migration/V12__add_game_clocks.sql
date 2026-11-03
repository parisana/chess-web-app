ALTER TABLE games
    ADD COLUMN white_remaining_millis BIGINT,
    ADD COLUMN black_remaining_millis BIGINT,
    ADD COLUMN clock_running_color VARCHAR(5),
    ADD COLUMN clock_turn_started_at TIMESTAMP WITH TIME ZONE;

-- Existing games remain untimed because all four new columns are NULL.
--
-- Explicit null checks prevent an incomplete clock from passing CHECK
-- through SQL's UNKNOWN result.
ALTER TABLE games
    ADD CONSTRAINT ck_games_clock
        CHECK (
            (
                white_remaining_millis IS NULL
                AND black_remaining_millis IS NULL
                AND clock_running_color IS NULL
                AND clock_turn_started_at IS NULL
            )
            OR
            (
                white_remaining_millis IS NOT NULL
                AND black_remaining_millis IS NOT NULL
                AND white_remaining_millis >= 0
                AND black_remaining_millis >= 0
                AND (
                    (
                        status = 'IN_PROGRESS'
                        AND clock_running_color IS NOT NULL
                        AND clock_running_color IN ('WHITE', 'BLACK')
                        AND clock_turn_started_at IS NOT NULL
                    )
                    OR
                    (
                        status = 'FINISHED'
                        AND clock_running_color IS NULL
                        AND clock_turn_started_at IS NULL
                    )
                )
            )
        );