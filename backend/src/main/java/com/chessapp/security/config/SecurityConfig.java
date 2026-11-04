package com.chessapp.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;

import java.util.List;

/**
 * @author pari on 11/03/26
 */
@Configuration
public class SecurityConfig {

    @Bean
    public HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(
            @Value("${server.servlet.session.cookie.secure:false}")
            boolean secure
    ) {
        // Angular reads this separate cookie and copies its value into
        // X-XSRF-TOKEN. The authentication cookie remains HttpOnly.
        var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();

        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .sameSite("Lax")
                .secure(secure)
        );

        return repository;
    }

    @Bean
    public SessionAuthenticationStrategy loginSessionStrategy(
            CookieCsrfTokenRepository csrfTokenRepository
    ) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                // Prevent an existing anonymous/old session ID from
                // becoming the authenticated session's unchanged ID.
                new ChangeSessionIdAuthenticationStrategy(),

                // Clear the previous CSRF token after successful login.
                new CsrfAuthenticationStrategy(csrfTokenRepository)
        ));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            HttpSessionSecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfTokenRepository,
            @Value("${server.servlet.session.cookie.name:CHESS_SESSION}")
            String sessionCookieName
    ) throws Exception {
        return http
                .csrf(csrf -> csrf
                        // Spring Security 7's SPA handling supports Angular's
                        // plain cookie token and BREACH-protected attributes.
                        .spa()
                        .csrfTokenRepository(csrfTokenRepository)
                )
                .securityContext(context -> context
                        .requireExplicitSave(true)
                        .securityContextRepository(
                                securityContextRepository
                        )
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.IF_REQUIRED
                        )
                )
                // API requests should receive 401, not create a saved
                // request and later redirect to an HTML login page.
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(
                                        HttpStatus.UNAUTHORIZED
                                )
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/auth/csrf"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies(sessionCookieName)
                        .logoutSuccessHandler((request, response, auth) -> {
                            response.setHeader(
                                    "Cache-Control",
                                    "no-store"
                            );
                            response.setStatus(
                                    HttpStatus.NO_CONTENT.value()
                            );
                        })
                        .permitAll()
                )
                .build();
    }
}