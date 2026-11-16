package com.ihomy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

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

    /**
     * 将上传目录映射为 urlPrefix 前缀的静态资源。
     * <p>
     * 上传文件名带时间戳(换图即换 URL),内容不会再被原地覆盖,所以可安全长缓存:
     * 家庭封面/头像/相册缩略图等很少变化,不该每次进页面都重新下载或回源校验。
     * 用 private 而不是 public:私有家庭图片只缓存在当前浏览器配置里,共享设备仍受登录态保护。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().toUri().toString();
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePrivate().immutable());
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
