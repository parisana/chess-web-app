package com.chessapp.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/**
 * @author pari on 10/16/26
 */
// the current @Size(max = 72) checks Java string length. BCrypt limits passwords to 72 UTF-8 bytes, so Unicode passwords can pass validation and still exceed the encoder’s limit.
public class BCryptPasswordLengthValidator
        implements ConstraintValidator<BCryptPasswordLength, String> {

    private static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(
            String value,
            ConstraintValidatorContext context
    ) {
//        Returning true for null is intentional: @NotBlank owns missing-value validation. Keeping those responsibilities separate follows Jakarta Validation’s guidance.
        return value == null
                || value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }
}
