package com.chessapp.auth.controller;

import com.chessapp.auth.dto.LoginRequest;
import com.chessapp.auth.service.LoginService;
import com.chessapp.player.dto.PlayerResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * @author pari on 10/16/26
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;

    @PostMapping("/login")
    public ResponseEntity<PlayerResponse> login(
            @Valid @RequestBody LoginRequest credentials,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        PlayerResponse player = loginService.login(
                credentials,
                request,
                response
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(player);
    }
}
