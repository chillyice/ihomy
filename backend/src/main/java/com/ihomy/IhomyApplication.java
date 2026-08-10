package com.ihomy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ihomy 后端启动类:Spring Boot 3 + MyBatis-Plus,
 * 扫描 com.ihomy.mapper(AOP 由 starter 自动装配),开启异步与定时任务支持。
 */
@SpringBootApplication
@MapperScan("com.ihomy.mapper")
@EnableAsync
@EnableScheduling
public class IhomyApplication {
    public static void main(String[] args) {
        SpringApplication.run(IhomyApplication.class, args);
    }
}
