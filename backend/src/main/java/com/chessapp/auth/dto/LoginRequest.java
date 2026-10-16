package com.chessapp.auth.dto;

import com.chessapp.auth.validation.BCryptPasswordLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @author pari on 10/16/26
 */
public record LoginRequest(

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @BCryptPasswordLength
        String password
) {
}

///  login does not enforce registration’s minimum password length. Login verifies an existing secret; changing registration policy later shouldn’t invalidate existing passwords.

