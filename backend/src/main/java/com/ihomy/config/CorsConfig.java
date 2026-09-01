package com.ihomy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 跨域配置:允许所有来源/头/方法(带凭证),
 * 开发期前端 5173 直连后端 8080 需要该配置。
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration cors = new CorsConfiguration();
        cors.addAllowedOriginPattern("*");
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
