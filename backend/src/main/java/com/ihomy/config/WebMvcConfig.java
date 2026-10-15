package com.ihomy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web MVC 配置:把上传目录映射为静态资源路径,
 * 使文件可经 /files/xxx 直接访问(nginx 同样托管该目录)。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.url-prefix}")
    private String urlPrefix;

    /** 将上传目录映射为 urlPrefix 前缀的静态资源 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().toUri().toString();
        registry.addResourceHandler(urlPrefix + "/**").addResourceLocations(location);
    }

    /**
     * 接口文档沿用历史入口 /doc.html → springdoc 原生页 /swagger-ui/index.html。
     * 不能把 springdoc.swagger-ui.path 直接设成 /doc.html:那样页面里 ./swagger-ui.css
     * 会解析到 /api/swagger-ui.css(不在安全白名单),匿名访客拿到的是空白页。
     * 注:Location 会是绝对地址且 scheme 取反代后的 http(Spring 7 恒输出绝对 URL),
     * 浏览器侧由 HSTS 升级到 https,实测可达;不必为此开 forward-headers-strategy。
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/doc.html", "/swagger-ui/index.html");
    }
}
