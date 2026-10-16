package com.chessapp.auth.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/16/26
 */
class RegisterRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @ParameterizedTest
    @MethodSource("validPasswords")
    void acceptsPasswordsWithinByteLimit(String password) {
        var violations = validator.validate(request(password));

        assertTrue(
                violations.isEmpty(),
                "A password within the configured limits should be valid"
        );
    }

    @ParameterizedTest
    @MethodSource("oversizedPasswords")
    void rejectsPasswordsExceedingByteLimit(String password) {
        var violations = validator.validate(request(password));

        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString().equals("password")
                        && violation.getMessage()
                        .equals("must not exceed 72 UTF-8 bytes")
        ));
    }

    static Stream<String> validPasswords() {
        return Stream.of(
                "a".repeat(72),            // 72 bytes
                "€".repeat(24),            // 72 bytes
                "a".repeat(68) + "😀"      // 72 bytes
        );
    }

    static Stream<String> oversizedPasswords() {
        return Stream.of(
                "a".repeat(73),            // 73 bytes
                "€".repeat(25),            // 75 bytes
                "a".repeat(69) + "😀"      // 73 bytes
        );
    }

    private static RegisterRequest request(String password) {
        return new RegisterRequest(
                "chessplayer1",
                "player1@example.com",
                password
        );
    }
}