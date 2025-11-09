package com.getjobs.worker.job51;

import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 * 前程无忧自动投递简历 - Playwright版本
 */
@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class Job51 {

    @Setter
    private Page page;

    @Setter
    private Job51Config config;

    @Setter
    private ProgressCallback progressCallback;

    @Setter
    private Supplier<Boolean> shouldStopCallback;

    /**
     * 进度回调接口
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void accept(String message, Integer current, Integer total);
    }

    /**
     * 准备工作：加载配置、初始化数据
     */
    public void prepare() {
        log.info("51job准备工作开始...");
        // TODO: 加载配置、初始化黑名单等
        log.info("51job准备工作完成");
    }

    /**
     * 执行投递任务
     * @return 投递数量
     */
    public int execute() {
        log.info("51job投递任务开始...");
        int deliveredCount = 0;

        try {
            // TODO: 实现投递逻辑
            // 1. 遍历关键词
            // 2. 搜索职位
            // 3. 批量投递
            // 4. 记录结果

            if (progressCallback != null) {
                progressCallback.accept("51job投递功能开发中...", null, null);
            }

            log.info("51job投递功能暂未实现，需要从Selenium迁移到Playwright");

        } catch (Exception e) {
            log.error("51job投递过程出现异常", e);
            if (progressCallback != null) {
                progressCallback.accept("投递出现异常: " + e.getMessage(), null, null);
            }
        }

        log.info("51job投递任务完成，共投递{}个职位", deliveredCount);
        return deliveredCount;
    }

    /**
     * 检查是否应该停止
     */
    private boolean shouldStop() {
        return shouldStopCallback != null && shouldStopCallback.get();
    }
}
