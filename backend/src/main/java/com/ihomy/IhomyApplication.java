package com.ihomy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * ihomy 后端启动类:Spring Boot 3 + MyBatis-Plus,
 * 扫描 com.ihomy.mapper,开启 AOP(暴露代理)与异步支持。
 */
@SpringBootApplication
@MapperScan("com.ihomy.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableAsync
public class IhomyApplication {
    public static void main(String[] args) {
        SpringApplication.run(IhomyApplication.class, args);
    }
}
