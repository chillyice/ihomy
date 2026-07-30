package com.ihomy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.ihomy.mapper")
public class IhomyApplication {
    public static void main(String[] args) {
        SpringApplication.run(IhomyApplication.class, args);
    }
}
