package com.getjobs.application.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.getjobs.worker.manager.PlaywrightManager;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;

/**
 * 应用启动后自动打开管理页面
 * 优先级：前端服务 > 静态资源 > 不打开
 */
@Slf4j
@Component
public class StartupRunner implements ApplicationRunner {

    @Value("${server.port:8888}")
    private int backendPort;

    private static final int FRONTEND_PORT = 6866;
    private static final String FRONTEND_URL = "http://localhost:" + FRONTEND_PORT;
    private static final String BACKEND_URL = "http://localhost:";

    @Autowired
    private PlaywrightManager playwrightManager;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String urlToOpen = determineUrlToOpen();
        if (urlToOpen != null) {
            openBrowser(urlToOpen);
        } else {
            log.info("未找到可用的管理页面，跳过自动打开浏览器");
        }

        // 在尝试打开管理页面之后，初始化 Playwright（满足“先打开管理页，再实例化”）
        try {
            playwrightManager.init();
        } catch (Exception e) {
            log.error("Playwright 初始化失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 确定要打开的URL
     * 优先级：前端服务 > 后端静态资源 > null
     */
    private String determineUrlToOpen() {
        // 1. 检查前端服务是否在运行
        if (isServiceRunning(FRONTEND_PORT)) {
            return FRONTEND_URL;
        }

        // 2. 检查后端是否有静态资源（dist文件夹）
        if (hasStaticResources()) {
            log.info("检测到后端静态资源，使用后端URL");
            return BACKEND_URL + backendPort;
        }

        // 3. 都没有，返回null
        log.info("未检测到前端服务或静态资源");
        return null;
    }

    /**
     * 检查指定端口的服务是否在运行
     * 支持 IPv4 和 IPv6
     */
    private boolean isServiceRunning(int port) {
        // 尝试多个地址：IPv4 和 IPv6
        String[] hosts = {"127.0.0.1", "[::1]", "localhost"};

        for (String host : hosts) {
            try {
                // 使用URI替代已过时的URL构造函数
                HttpURLConnection connection = (HttpURLConnection) URI.create("http://" + host + ":" + port)
                        .toURL()
                        .openConnection();

                connection.setConnectTimeout(2000);
                connection.setReadTimeout(2000);
                connection.setRequestMethod("GET");
                connection.setInstanceFollowRedirects(false);

                int responseCode = connection.getResponseCode();
                connection.disconnect();

                // 接受所有2xx, 3xx, 4xx响应码（说明服务在运行）
                if (responseCode >= 200 && responseCode < 500) {
                    log.debug("检测到端口 {} 的服务 (地址: {})", port, host);
                    return true;
                }
            } catch (Exception e) {
                log.debug("端口 {} HTTP服务检测失败 ({}): {}", port, host, e.getMessage());
            }
        }

        return false;
    }

    /**
     * 检查是否存在静态资源文件夹
     * 检查 src/main/resources/dist/ 是否存在且包含文件
     */
    private boolean hasStaticResources() {
        try {
            // 检查dist文件夹是否存在
            java.io.File distDir = new java.io.File("src/main/resources/dist");
            if (distDir.exists() && distDir.isDirectory()) {
                // 检查是否有文件（不包括隐藏文件）
                java.io.File[] files = distDir.listFiles(file -> !file.getName().startsWith("."));
                if (files != null && files.length > 0) {
                    log.info("发现静态资源文件夹: {}, 文件数: {}", distDir.getAbsolutePath(), files.length);
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("检查静态资源时出错: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 打开默认浏览器
     */
    private void openBrowser(String url) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder processBuilder;

            if (os.contains("win")) {
                processBuilder = new ProcessBuilder("cmd", "/c", "start", "", url);
            } else if (os.contains("mac")) {
                processBuilder = new ProcessBuilder("open", url);
            } else if (os.contains("nix") || os.contains("nux")) {
                processBuilder = new ProcessBuilder("xdg-open", url);
            } else {
                log.warn("未知操作系统类型，无法自动打开浏览器，请手动访问: {}", url);
                return;
            }

            processBuilder.start();
            log.info("已打开浏览器: {}", url);
        } catch (IOException e) {
            log.error("打开浏览器失败: {}", e.getMessage());
            log.info("请手动访问管理页面: {}", url);
        }
    }
}
