package com.chessapp.auth.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
/**
 * @author pari on 11/3/26

 * GET  /auth/csrf       → obtain XSRF-TOKEN cookie
 * POST /auth/login     → send cookie + X-XSRF-TOKEN header
 * GET  /auth/csrf       → obtain current token after authentication
 * GET  /players/me     → restore/read authenticated Player

 * Call the CSRF endpoint on application initialization and after successful login/logout.
 * Spring Security clears the previous CSRF token during authentication and logout.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class CsrfController {

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        // Resolve the deferred token so its cookie is written.
        csrfToken.getToken();

        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
