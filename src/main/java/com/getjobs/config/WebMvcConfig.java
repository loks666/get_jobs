package com.getjobs.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web静态资源配置
 * 配置前端静态文件访问
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 配置静态资源处理
     * 将根路径映射到dist文件夹
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射根路径到dist文件夹
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/dist/")
                .setCachePeriod(3600); // 缓存1小时
    }

    /**
     * 配置视图控制器
     * 将根路径转发到index.html
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/")
                .setViewName("forward:/index.html");
    }
}
