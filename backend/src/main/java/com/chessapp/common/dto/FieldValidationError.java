package com.chessapp.common.dto;

/**
 * @author pari on 10/16/26
 */
public record FieldValidationError(
        String field,
        String message
) {
}
