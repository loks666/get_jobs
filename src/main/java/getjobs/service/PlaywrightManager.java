package getjobs.service;

import getjobs.utils.PlaywrightUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Playwright管理器 - 单例管理浏览器实例
 * 在应用启动时初始化，应用关闭时清理
 * 
 * @author loks666
 *         项目链接: <a href=
 *         "https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Slf4j
@Component
public class PlaywrightManager {

    private volatile boolean initialized = false;
    
    @Value("${server.port:8080}")
    private int serverPort;

    /**
     * Spring容器启动后初始化Playwright
     */
    @PostConstruct
    public void initializePlaywright() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    log.info("正在初始化Playwright浏览器实例...");
                    try {
                        PlaywrightUtil.init();
                        initialized = true;
                        log.info("Playwright浏览器实例初始化成功");
                    } catch (Exception e) {
                        log.error("Playwright浏览器初始化失败", e);
                    }
                }
            }
        }
    }

    /**
     * 应用完全启动后导航到主页
     */
    @EventListener(ApplicationReadyEvent.class)
    public void navigateToHomePage() {
        if (initialized) {
            try {
                log.info("应用已启动完成，正在导航到主页: http://localhost:{}", serverPort);
                PlaywrightUtil.getPageObject().navigate("http://localhost:" + serverPort);
                PlaywrightUtil.getPageObject().waitForLoadState();
                log.info("已成功导航到主页: http://localhost:{}", serverPort);
            } catch (Exception e) {
                log.warn("导航到主页失败: http://localhost:{}，错误: {}", serverPort, e.getMessage());
            }
        }
    }

    /**
     * Spring容器关闭前清理Playwright资源
     */
    @PreDestroy
    public void cleanup() {
        if (initialized) {
            log.info("正在清理Playwright资源...");
            try {
                PlaywrightUtil.close();
                initialized = false;
                log.info("Playwright资源清理完成");
            } catch (Exception e) {
                log.error("Playwright资源清理失败", e);
            }
        }
    }

    /**
     * 确保Playwright已初始化
     */
    public void ensureInitialized() {
        if (!initialized) {
            log.info("正在确保Playwright初始化...");
            try {
                PlaywrightUtil.init();
                initialized = true;
                log.info("Playwright浏览器实例初始化成功");
            } catch (Exception e) {
                log.error("Playwright浏览器初始化失败", e);
                throw new RuntimeException("Playwright初始化失败", e);
            }
        }
    }
}
