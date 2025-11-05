package com.getjobs.config;

import org.apache.catalina.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 静态资源服务器配置
 * 当前端 dev 服务未运行时，在 6866 端口提供静态资源
 */
@Configuration
public class StaticServerConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(StaticServerConfiguration.class);
    private static final int FRONTEND_PORT = 6866;
    private static final String DIST_PATH = "src/main/resources/dist";
    private static final String STATIC_PATH = "src/main/resources/static";

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> servletContainer() {
        return server -> {
            // 检查前端 dev 服务是否运行
            boolean hasFrontendDev = detectFrontendDevServer();

            if (hasFrontendDev) {
                logger.info("检测到前端开发服务运行在端口 {}", FRONTEND_PORT);
                return;
            }

            // 检查是否有静态资源
            boolean hasStaticResources = checkStaticResources();

            if (hasStaticResources) {
                logger.info("未检测到前端开发服务，但找到静态资源");
                logger.info("配置额外端口 {} 用于提供静态资源", FRONTEND_PORT);

                Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
                connector.setPort(FRONTEND_PORT);
                server.addAdditionalTomcatConnectors(connector);
            } else {
                logger.warn("未检测到前端开发服务，也未找到静态资源");
            }
        };
    }

    /**
     * 探测前端开发服务器是否在运行
     * 尝试 IPv4 和 IPv6
     */
    private boolean detectFrontendDevServer() {
        // 尝试多个地址：IPv4 和 IPv6
        String[] hosts = {"127.0.0.1", "[::1]", "localhost"};

        for (String host : hosts) {
            try {
                URL url = new URL("http://" + host + ":" + FRONTEND_PORT);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(2000);
                connection.setInstanceFollowRedirects(false);

                int responseCode = connection.getResponseCode();
                connection.disconnect();

                if (responseCode >= 200 && responseCode < 500) {
                    logger.info("检测到前端开发服务运行在端口 {} (地址: {})", FRONTEND_PORT, host);
                    return true;
                }
            } catch (IOException e) {
                logger.debug("检测前端服务失败 ({}): {}", host, e.getMessage());
            }
        }

        logger.debug("前端开发服务检测失败，已尝试所有地址");
        return false;
    }

    /**
     * 检查静态资源是否存在
     */
    private boolean checkStaticResources() {
        Path distPath = Paths.get(DIST_PATH);
        Path staticPath = Paths.get(STATIC_PATH);

        logger.info("检查静态资源路径:");
        logger.info("  dist路径: {} (绝对路径: {})", DIST_PATH, distPath.toAbsolutePath());
        logger.info("  static路径: {} (绝对路径: {})", STATIC_PATH, staticPath.toAbsolutePath());

        boolean hasDist = hasContent(distPath);
        boolean hasStatic = hasContent(staticPath);

        logger.info("  dist存在: {}", hasDist);
        logger.info("  static存在: {}", hasStatic);

        return hasDist || hasStatic;
    }

    /**
     * 检查目录是否有内容
     */
    private boolean hasContent(Path path) {
        try {
            if (!Files.exists(path)) {
                return false;
            }
            return Files.list(path).findAny().isPresent();
        } catch (IOException e) {
            logger.error("检查路径失败: {}", path, e);
            return false;
        }
    }
}
