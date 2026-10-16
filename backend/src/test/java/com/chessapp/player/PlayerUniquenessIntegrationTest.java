package com.chessapp.player;

import com.chessapp.player.entity.Player;
import com.chessapp.player.exception.PlayerErrorCodes;
import com.chessapp.player.exception.PlayerExceptionHandler;
import com.chessapp.player.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/16/26
 * Test actual PostgreSQL uniqueness violations
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlayerUniquenessIntegrationTest {

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerExceptionHandler exceptionHandler;

    @Test
    void duplicateUsername_mapsToUsernameConflict() {
        String suffix = uniqueSuffix();
        String username = "player_" + suffix;

        /// //// We deliberately call the repository directly. Going through PlayerService would trigger the existence checks and never exercise the database fallback. //////
        playerRepository.saveAndFlush(
                new Player(username, suffix + "@example.com")
        );

        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                () -> playerRepository.saveAndFlush(
                        new Player(
                                username,
                                suffix + "_other@example.com"
                        )
                )
        );

        assertConflict(exception, PlayerErrorCodes.USERNAME_ALREADY_EXISTS);
    }

    @Test
    void duplicateEmail_mapsToEmailConflict() {
        String suffix = uniqueSuffix();
        String email = suffix + "@example.com";

        playerRepository.saveAndFlush(
                new Player("first_" + suffix, email)
        );

        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                () -> playerRepository.saveAndFlush(
                        new Player("second_" + suffix, email)
                )
        );

        assertConflict(exception, PlayerErrorCodes.EMAIL_ALREADY_EXISTS);
    }

    private void assertConflict(
            DataIntegrityViolationException exception,
            String expectedCode
    ) {
        ProblemDetail problem =
                exceptionHandler.handleDataIntegrityViolation(exception);

        assertEquals(409, problem.getStatus());
        assertNotNull(problem.getProperties());
        assertEquals(expectedCode, problem.getProperties().get("code"));
    }

    private static String uniqueSuffix() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);
    }
}
