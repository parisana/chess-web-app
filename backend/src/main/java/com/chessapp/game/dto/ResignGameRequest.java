package com.chessapp.game.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * @author pari on 10/30/26
 */
public record ResignGameRequest(
        @NotNull
        @PositiveOrZero
        Long expectedVersion
) {
}
