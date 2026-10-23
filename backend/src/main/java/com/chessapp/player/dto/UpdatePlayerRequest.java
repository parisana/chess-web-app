package com.chessapp.player.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @author pari on 10/23/26
 */
public record UpdatePlayerRequest(

        @NotBlank
        @Size(min = 3, max = 30)
        String username
) {
}
