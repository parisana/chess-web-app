package com.chessapp.support;

import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
/**
 * @author pari on 11/4/26
 */
public final class TestCsrf {

    private TestCsrf() {
    }

    /**
     * Supply a matching CSRF cookie/header pair without replacing
     * the application's CSRF repository or request handler.
     */
    public static RequestPostProcessor cookieCsrf() {
        return request -> {
            var repository =
                    CookieCsrfTokenRepository.withHttpOnlyFalse();

            var token = repository.generateToken(request);
            var response = new MockHttpServletResponse();

            repository.saveToken(token, request, response);

            Cookie csrfCookie = response.getCookie("XSRF-TOKEN");

            if (csrfCookie == null) {
                throw new IllegalStateException(
                        "Expected a CSRF cookie"
                );
            }

            List<Cookie> cookies = new ArrayList<>();

            if (request.getCookies() != null) {
                cookies.addAll(Arrays.asList(request.getCookies()));
            }

            // Preserve authentication cookies and replace only CSRF.
            cookies.removeIf(cookie ->
                    cookie.getName().equals(csrfCookie.getName())
            );
            cookies.add(csrfCookie);

            request.setCookies(cookies.toArray(Cookie[]::new));

            // Avoid conflicting values if the fixture is applied twice.
            request.removeHeader(token.getHeaderName());
            request.addHeader(
                    token.getHeaderName(),
                    token.getToken()
            );

            return request;
        };
    }
}
