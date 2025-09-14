package getjobs.service;

import getjobs.utils.PlaywrightUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
                        throw new RuntimeException("Playwright初始化失败", e);
                    }
                }
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
     * 检查Playwright是否已初始化
     * 
     * @return 是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 确保Playwright已初始化
     */
    public void ensureInitialized() {
        if (!initialized) {
            initializePlaywright();
        }
    }
}
