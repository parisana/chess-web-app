package com.chessapp.auth.controller;

import com.chessapp.auth.dto.RegisterResponse;
import com.chessapp.auth.service.AuthService;
import com.chessapp.common.exception.PlayerExceptionHandler;
import com.chessapp.player.exception.DuplicatePlayerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author pari on 10/16/26
 */
class AuthControllerTest {

    private MockMvc mockMvc;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);

//      Note that standalone MockMvc does not execute the application's Spring Security filter chain. We'll test endpoint security separately.
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new PlayerExceptionHandler())
                .build();
    }

//   Test Spring MVC maps the request to AuthController. & The controller delegates valid requests to AuthService
    @Test
    void register_returnsCreatedForValidRequest() throws Exception {
        when(authService.register(any()))
                .thenReturn(new RegisterResponse(
                        1L,
                        "chessplayer1",
                        "player1@example.com"
                ));

        String request = """
                {
                  "username": "chessplayer1",
                  "email": "player1@example.com",
                  "password": "StrongPassword123!"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playerId").value(1))
                .andExpect(jsonPath("$.username").value("chessplayer1"));

        verify(authService).register(any());
    }

//    Test Bean Validation rejects invalid request DTOs.
    @Test
    void register_returnsBadRequestForInvalidEmail() throws Exception {
        String request = """
                {
                  "username": "chessplayer1",
                  "email": "invalid-email",
                  "password": "StrongPassword123!"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void register_returnsBadRequestForShortPassword() throws Exception {
        String request = """
                {
                  "username": "chessplayer1",
                  "email": "player1@example.com",
                  "password": "short"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void register_returnsConflictForDuplicateUsername() throws Exception {
        when(authService.register(any()))
                .thenThrow(new DuplicatePlayerException(
                        "USERNAME_ALREADY_EXISTS",
                        "Username is already taken"
                ));

        String request = """
            {
              "username": "chessplayer1",
              "email": "player1@example.com",
              "password": "StrongPassword123!"
            }
            """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("USERNAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.detail")
                        .value("Username is already taken"));
    }
}