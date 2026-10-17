package com.chessapp.auth.controller;

import com.chessapp.auth.dto.LoginRequest;
import com.chessapp.auth.dto.LoginResponse;
import com.chessapp.auth.service.LoginService;
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
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore()) // tells HTTP caches not to retain the token response
                .body(loginService.login(request));
    }
}
