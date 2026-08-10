package com.ihomy.config;

import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.security.JwtAuthenticationFilter;
import com.ihomy.security.OpsAccessFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置:无状态 JWT 认证,
 * 公开接口白名单放行,其余请求需登录;未认证/无权限统一返回 JSON。
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OpsAccessFilter opsAccessFilter;
    private final ObjectMapper objectMapper;

    /** 密码加密器:统一使用 BCrypt */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(c -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 认证/文件/公开接口/接口文档无需登录;ws 握手自校验 JWT
                .requestMatchers("/auth/**", "/files/**", "/public/**", "/ws/**",
                        "/doc.html", "/webjars/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                // 读类接口(列表/详情/评论/点赞状态)也允许游客访问
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                        "/home/modules", "/home/feed", "/blog/list", "/blog/*", "/diary/list",
                        "/album/**", "/anniversary/list", "/comment/list", "/like/state").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                // 未登录与无权限均以统一 JSON 结构返回,而非跳转登录页
                .authenticationEntryPoint((req, resp, ex) -> {
                    resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    resp.setCharacterEncoding("UTF-8");
                    resp.getWriter().write(objectMapper.writeValueAsString(Result.fail(ResultCode.UNAUTHORIZED)));
                })
                .accessDeniedHandler((req, resp, ex) -> {
                    resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    resp.setCharacterEncoding("UTF-8");
                    resp.getWriter().write(objectMapper.writeValueAsString(Result.fail(ResultCode.FORBIDDEN)));
                }))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // OPS 角色白名单限制（放在 JWT 过滤器之后,认证完成后拦截）
            .addFilterAfter(opsAccessFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
