package com.getjobs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 静态资源配置
 * 配置前端静态资源的访问路径
 */
@Configuration
public class StaticResourceConfiguration implements WebMvcConfigurer {
    private static final Logger logger = LoggerFactory.getLogger(StaticResourceConfiguration.class);
    private static final int FRONTEND_PORT = 6866;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 检查前端服务是否运行
        boolean hasFrontendService = detectFrontendService();

        // 检查静态资源路径
        Path distPath = Paths.get("src/main/resources/dist");
        Path staticPath = Paths.get("src/main/resources/static");

        boolean hasDistResources = Files.exists(distPath);
        boolean hasStaticResources = Files.exists(staticPath);

        if (hasDistResources || hasStaticResources) {
            logger.info("配置静态资源服务:");

            // 根据前端服务运行状态输出不同日志
            if (hasFrontendService) {
                logger.info("  - 使用前端开发服务 (端口 {})", FRONTEND_PORT);
            } else {
                if (hasDistResources) {
                    logger.info("  - 使用 dist 目录: {}", distPath.toAbsolutePath());
                }
                if (hasStaticResources) {
                    logger.info("  - 使用 static 目录: {}", staticPath.toAbsolutePath());
                }
            }

            // 配置静态资源处理器
            registry.addResourceHandler("/**")
                    .addResourceLocations(
                            "classpath:/dist/",
                            "classpath:/static/",
                            "file:src/main/resources/dist/",
                            "file:src/main/resources/static/"
                    )
                    .resourceChain(true)
                    .addResolver(new PathResourceResolver() {
                        @Override
                        protected Resource getResource(String resourcePath, Resource location) {
                            try {
                                Resource requestedResource = location.createRelative(resourcePath);

                                // 如果请求的资源存在，直接返回
                                if (requestedResource.exists() && requestedResource.isReadable()) {
                                    return requestedResource;
                                }

                                // 对于 SPA 应用，如果资源不存在且不是 API 请求，返回 index.html
                                if (!resourcePath.startsWith("api/") && !resourcePath.contains(".")) {
                                    Resource indexResource = location.createRelative("index.html");
                                    if (indexResource.exists() && indexResource.isReadable()) {
                                        return indexResource;
                                    }
                                }
                            } catch (IOException e) {
                                // 返回 null
                            }

                            return null;
                        }
                    });
        } else {
            logger.warn("未找到静态资源目录 (dist 或 static)");
        }
    }

    /**
     * 检测前端开发服务是否在运行
     */
    private boolean detectFrontendService() {
        String[] hosts = {"127.0.0.1", "[::1]", "localhost"};

        for (String host : hosts) {
            try {
                URL url = new URL("http://" + host + ":" + FRONTEND_PORT);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(1000);
                connection.setReadTimeout(1000);
                connection.setInstanceFollowRedirects(false);

                int responseCode = connection.getResponseCode();
                connection.disconnect();

                if (responseCode >= 200 && responseCode < 500) {
                    return true;
                }
            } catch (IOException e) {
                // 继续尝试下一个地址
            }
        }

        return false;
    }
}
