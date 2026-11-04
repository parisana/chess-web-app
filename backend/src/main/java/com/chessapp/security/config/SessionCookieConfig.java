package com.chessapp.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import java.time.Duration;
/**
 * @author pari on 11/4/26
 */
@Configuration(proxyBeanMethods = false)
public class SessionCookieConfig {

    @Bean
    public CookieSerializer cookieSerializer(
            @Value("${server.servlet.session.cookie.name}")
            String name,
            @Value("${server.servlet.session.cookie.path:/}")
            String path,
            @Value("${server.servlet.session.cookie.http-only:true}")
            boolean httpOnly,
            @Value("${server.servlet.session.cookie.secure:false}")
            boolean secure,
            @Value("${server.servlet.session.cookie.same-site:lax}")
            String sameSite,
            @Value("${server.servlet.session.cookie.max-age}")
            Duration maxAge
    ) {
        var serializer = new DefaultCookieSerializer();

        serializer.setCookieName(name);
        serializer.setCookiePath(path);
        serializer.setUseHttpOnlyCookie(httpOnly);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite(sameSite);
        serializer.setCookieMaxAge(
                Math.toIntExact(maxAge.toSeconds())
        );

        return serializer;
    }
}
