package com.ihomy.garden.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * garden 模块的 Mapper 注册:主类 @MapperScan 只扫 com.ihomy.mapper,
 * garden 包内的 Mapper 在这里单独扫描(不改主类)。
 */
@Configuration
@MapperScan("com.ihomy.garden.mapper")
public class GardenMapperConfig {
}
