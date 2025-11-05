package com.getjobs.application.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Mapper配置
 * 扫描 com.getjobs.application.mapper 包下的所有 Mapper接口
 */
@Configuration
@MapperScan("com.getjobs.application.mapper")
public class DataMapperConfig {
}
