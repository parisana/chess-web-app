package com.chessapp.player.service;

import com.chessapp.player.exception.DuplicatePlayerException;
import com.chessapp.player.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author pari on 10/16/26
 */
@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private PlayerService playerService;

    @Test
    void createPlayer_rejectsDuplicateUsernameWithoutSaving() {
        when(playerRepository.existsByUsername("pari"))
                .thenReturn(true);

        DuplicatePlayerException exception = assertThrows(
                DuplicatePlayerException.class,
                () -> playerService.createPlayer(
                        "pari",
                        "pari@example.com"
                )
        );

        assertEquals(
                "USERNAME_ALREADY_EXISTS",
                exception.getCode()
        );
        // verifies the save method was never called
        verify(playerRepository, never()).save(any());
    }

    @Test
    void createPlayer_rejectsDuplicateEmailWithoutSaving() {
        when(playerRepository.existsByEmail("pari@example.com"))
                .thenReturn(true);

        DuplicatePlayerException exception = assertThrows(
                DuplicatePlayerException.class,
                () -> playerService.createPlayer(
                        "pari",
                        "pari@example.com"
                )
        );

        assertEquals(
                "EMAIL_ALREADY_EXISTS",
                exception.getCode()
        );
        verify(playerRepository, never()).save(any());
    }
}