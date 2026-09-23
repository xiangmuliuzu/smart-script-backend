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
 * Dedicated App credential-domain filter chain for the App-only API surface.
 *
 * A3 只覆盖 /api/v1/auth/**；A5 把 App 用户中心并入同一凭证域：
 *   /api/v1/users/**    资料、实名、账号安全（含换绑手机号）、通知偏好
 *   /api/v1/messages/** 消息中心
 *   /api/v1/feedback/** 意见反馈
 *
 * 边界不变：PC 管理接口（/api/v1/admin/**）与若依原生接口仍走若依过滤链，
 * 因此 App Token 访问管理端仍被拒绝，PC Token 访问 App 私有接口也不被本链接受。
 */
@Configuration
public class AppAuthSecurityConfig
{
    /**
     * App 凭证域覆盖的路径前缀；本链之外的前缀一律 denyAll。
     *
     * A6 起纳入 /api/v1/content/**：业务模块（B/C/D/E）的接口需要按 App 凭证域鉴权，
     * 其中公开接口在过滤器白名单中登记（游客可读），私有接口默认需要 App 身份。
     */
    static final String[] APP_PATH_PREFIXES = {
            "/api/v1/auth/**", "/api/v1/users/**", "/api/v1/messages/**", "/api/v1/feedback/**",
            "/api/v1/content/**"
    };

    @Bean
    @Order(100)
    public SecurityFilterChain appAuthFilterChain(HttpSecurity http,
            AppAccessTokenService accessTokenService,
            AppAuthEntryPoint appAuthEntryPoint) throws Exception
    {
        AppAuthAuthenticationFilter appFilter = new AppAuthAuthenticationFilter(accessTokenService);
        http.securityMatcher(APP_PATH_PREFIXES)
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
                        // A6 业务模块公开接口：游客可读（无需 App Token）
                        .requestMatchers(HttpMethod.GET, "/api/v1/content/works").permitAll()
                        // App 用户中心 / 消息 / 反馈 / 业务模块私有接口：全部要求 App Access Token
                        .requestMatchers("/api/v1/auth/**", "/api/v1/users/**",
                                "/api/v1/messages/**", "/api/v1/feedback/**",
                                "/api/v1/content/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(appFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

