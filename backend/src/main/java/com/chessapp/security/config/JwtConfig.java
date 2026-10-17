package com.chessapp.security.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * @author pari on 10/16/26
 */
@Configuration
public class JwtConfig {

    private static boolean isValidPlayerId(String subject) {
        if (subject == null) {
            return false;
        }

        try {
            return Long.parseLong(subject) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    @Bean
    public SecretKey jwtSigningKey(
            @Value("${app.jwt.secret}") String base64Secret
    ) {
        byte[] bytes = Base64.getDecoder().decode(base64Secret);

        if (bytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT signing key must contain at least 32 decoded bytes"
            );
        }

        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(
                new ImmutableSecret<>(jwtSigningKey)
        );
    }

    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey jwtSigningKey,
            Clock clock,
            @Value("${app.jwt.issuer}") String issuer
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        /// The 60-second tolerance accommodates small clock differences. A token may therefore remain acceptable briefly after its stated expiration.
        JwtTimestampValidator timestamps =
                new JwtTimestampValidator(Duration.ofSeconds(60));
        timestamps.setClock(clock);

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        timestamps,
                        new JwtIssuerValidator(issuer),
                        new JwtClaimValidator<Instant>(
                                "exp",
                                Objects::nonNull
                        ),
                        new JwtClaimValidator<String>("sub", JwtConfig::isValidPlayerId)
                )
        );

        return decoder;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
