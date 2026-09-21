package com.smartscript.platform.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.smartscript.platform.user.security.AppAccessTokenService;
import com.smartscript.platform.user.security.AppAuthAuthenticationFilter;
import com.smartscript.platform.user.security.AppAuthEntryPoint;

/**
 * Dedicated App credential-domain filter chain for /api/v1/auth/**.
 * PC management endpoints remain on the RuoYi filter chain.
 */
@Configuration
public class AppAuthSecurityConfig
{
    @Bean
    @Order(100)
    public SecurityFilterChain appAuthFilterChain(HttpSecurity http,
            AppAccessTokenService accessTokenService,
            AppAuthEntryPoint appAuthEntryPoint) throws Exception
    {
        AppAuthAuthenticationFilter appFilter = new AppAuthAuthenticationFilter(accessTokenService);
        http.securityMatcher("/api/v1/auth/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(appAuthEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/sms/send",
                                "/api/v1/auth/sms/login",
                                "/api/v1/auth/password/login",
                                "/api/v1/auth/password/reset",
                                "/api/v1/auth/register",
                                "/api/v1/auth/token/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/auth/agreements").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/oauth/wechat/login",
                                "/api/v1/auth/oauth/qq/login").permitAll()
                        .requestMatchers("/api/v1/auth/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(appFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

