package com.ihomy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.ihomy.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableAsync
public class IhomyApplication {
    public static void main(String[] args) {
        SpringApplication.run(IhomyApplication.class, args);
    }
}
