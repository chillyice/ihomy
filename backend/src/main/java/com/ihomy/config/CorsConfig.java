package com.ihomy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 跨域配置:仅放行白名单来源(app.cors-allowed-origins,经 external.yml 按环境覆盖),允许携带凭证。
 * 默认 https://ihomy.top(生产) + 本机 5173(开发,前端 dev)。
 * 不再反射任意来源——此前 addAllowedOriginPattern("*") + allowCredentials 任意站点可带凭证读响应。
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors-allowed-origins:https://ihomy.top,http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration cors = new CorsConfiguration();
        for (String o : allowedOrigins.split(",")) {
            String origin = o.trim();
            if (!origin.isEmpty()) {
                cors.addAllowedOriginPattern(origin);
            }
        }
        cors.addAllowedHeader("*");
        cors.addAllowedMethod("*");
        // 暴露链路追踪头给前端(报错 toast 展示 tid,便于到运维"详细日志"页检索)
        cors.addExposedHeader("X-Trace-Id");
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);
        source.registerCorsConfiguration("/**", cors);
        return new CorsFilter(source);
    }
}
