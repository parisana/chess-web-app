package com.chessapp.common.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/16/26
 */
class PlayerExceptionHandlerTest {

    private final PlayerExceptionHandler handler =
            new PlayerExceptionHandler();

    @ParameterizedTest
    @CsvSource({
            "uk_players_username, USERNAME_ALREADY_EXISTS",
            "uk_players_email, EMAIL_ALREADY_EXISTS"
    })
    void mapsKnownConstraintsToConflict(
            String constraint,
            String expectedCode
    ) {
        ProblemDetail problem = handler.handleDataIntegrityViolation(
                integrityViolation(constraint)
        );

        assertEquals(409, problem.getStatus());
        assertNotNull(problem.getProperties());
        assertEquals(
                expectedCode,
                problem.getProperties().get("code")
        );
    }

    @Test
    void rethrowsUnknownConstraint() {
        DataIntegrityViolationException exception =
                integrityViolation("fk_auth_credentials_player");

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> handler.handleDataIntegrityViolation(exception)
        );

        assertSame(exception, thrown);
    }

    @Test
    void rethrowsWhenConstraintInformationIsUnavailable() {
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("Unknown failure");

        assertSame(
                exception,
                assertThrows(
                        DataIntegrityViolationException.class,
                        () -> handler.handleDataIntegrityViolation(exception)
                )
        );
    }

    private static DataIntegrityViolationException integrityViolation(
            String constraint
    ) {
        ConstraintViolationException cause =
                new ConstraintViolationException(
                        "Constraint violation",
                        new SQLException("Synthetic test failure", "23505"),
                        constraint
                );

        return new DataIntegrityViolationException(
                "Could not persist entity",
                cause
        );
    }
}