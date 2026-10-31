package com.chessapp.game.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * @author pari on 10/30/26
 * Version precondition shared by draw-offer commands.
 * The authenticated JWT determines the acting Player.
 */
public record DrawOfferRequest(
        @NotNull
        @PositiveOrZero
        Long expectedVersion
) {
}
