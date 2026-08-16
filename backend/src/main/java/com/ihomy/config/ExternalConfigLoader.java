package com.ihomy.config;

import com.ihomy.common.AesUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;
import java.util.Properties;

/**
 * 外挂配置加载器:在 Spring Boot 启动早期(DataSource/Redis 初始化前)
 * 从环境变量 IHOMY_CONFIG_PATH 指定的 yml 文件加载配置,注入到 Environment。
 *
 * 外挂文件含敏感凭证:MySQL/Redis 密码、邮件 SMTP 密码、JWT 密钥、天气四件套。
 * DB 密码必须明文(鸡生蛋:DB 未连上无法读盐值解密)。
 * 其他敏感信息(Redis/JWT/邮件/天气)可用 ENC(...) 格式加密,加载时用环境变量
 * IHOMY_AES_SALT 指定的盐值解密后注入明文。
 *
 * 加载顺序:application.yml → application-{profile}.yml → 外挂文件(最高优先级)
 */
@Slf4j
public class ExternalConfigLoader implements EnvironmentPostProcessor {

    private static final String ENV_CONFIG = "IHOMY_CONFIG_PATH";
    private static final String ENV_SALT = "IHOMY_AES_SALT";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String path = System.getenv(ENV_CONFIG);
        if (path == null || path.isBlank()) {
            log.info("External config not loaded: env var {} not set", ENV_CONFIG);
            return;
        }
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            log.warn("External config not loaded: file not found at {}", path);
            return;
        }
        String salt = System.getenv(ENV_SALT);
        try {
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            List<PropertySource<?>> sources = loader.load("external-config", new FileSystemResource(file));
            for (PropertySource<?> ps : sources) {
                if (salt != null && !salt.isBlank() && ps instanceof EnumerablePropertySource<?> eps) {
                    // 有盐值:解密 ENC(...) 值后注入
                    Properties props = new Properties();
                    for (String name : eps.getPropertyNames()) {
                        Object val = eps.getProperty(name);
                        if (val == null) continue;
                        String strVal = val.toString();
                        if (AesUtil.isEncrypted(strVal)) {
                            try {
                                strVal = AesUtil.decrypt(strVal, salt);
                            } catch (Exception e) {
                                log.error("Failed to decrypt property {}: {}", name, e.getMessage());
                                throw e;
                            }
                        }
                        props.setProperty(name, strVal);
                    }
                    environment.getPropertySources().addFirst(
                        new PropertiesPropertySource("external-config", props));
                    log.info("External config loaded (with AES decryption) from {}", path);
                } else {
                    // 无盐值:直接加载(明文模式)
                    environment.getPropertySources().addFirst(ps);
                    log.info("External config loaded (plaintext) from {}", path);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load external config from {}: {}", path, e.getMessage());
        }
    }
}
