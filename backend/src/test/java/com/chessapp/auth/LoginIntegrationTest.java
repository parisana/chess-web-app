package com.chessapp.auth;

import com.chessapp.auth.dto.RegisterRequest;
import com.chessapp.auth.service.AuthService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @author pari on 10/17/26
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Test
    void login_returnsTokenThatCanAccessCurrentPlayer() throws Exception {
        RegisterRequest registration = newRegistration();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(
                                registration.username(),
                                registration.email(),
                                registration.password()
                        )))
                .andExpect(status().isCreated());

        String response = login(
                registration.email(),
                registration.password()
        )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.accessToken")
                        .value(not(emptyOrNullString())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(greaterThan(0)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = JsonPath.read(response, "$.accessToken");

        mockMvc.perform(get("/api/v1/players/me")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username")
                        .value(registration.username()))
                .andExpect(jsonPath("$.email")
                        .value(registration.email()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void login_rejectsIncorrectPassword() throws Exception {
        RegisterRequest registration = newRegistration();
        authService.register(registration);

        login(registration.email(), "WrongPassword123!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail")
                        .value("Invalid email or password"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void login_rejectsUnknownEmail() throws Exception {
        RegisterRequest registration = newRegistration();

        login(registration.email(), registration.password())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail")
                        .value("Invalid email or password"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void login_rejectsInvalidEmailFormat() throws Exception {
        login("invalid-email", "StrongPassword123!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(hasItem("email")));
    }

    private ResultActions login(String email, String password)
            throws Exception {

        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(email, password)));
    }

    private static RegisterRequest newRegistration() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);

        return new RegisterRequest(
                "player_" + suffix,
                suffix + "@example.com",
                "StrongPassword123!"
        );
    }
}
