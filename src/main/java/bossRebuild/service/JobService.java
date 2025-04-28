package bossRebuild.service;

import ai.AiConfig;
import ai.AiFilter;
import ai.AiService;
import bossRebuild.config.BossConfig;
import lombok.SneakyThrows;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Job;
import utils.JobUtils;
import utils.SeleniumUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static bossRebuild.constants.Constants.BASE_URL;
import static utils.Constant.*;

public class JobService {
    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private final WebDriverWait wait;
    private final BossConfig config;
    private final List<Job> resultList;
    private final FilterService filterService;
    private final Set<String> submittedJobs;
    private int noJobPages;

    // 新增计数器
    private int successfulSubmissions = 0;
    private int consecutiveErrors = 0;
    private static final int MAX_SUCCESSFUL_SUBMISSIONS = 100;
    private static final int MAX_CONSECUTIVE_ERRORS = 5;

    private static class Elements {
        public static final String JOB_LIST_XPATH = "//*[@id=\"wrap\"]/div[2]/div[3]/div/div/div[1]/ul";
        public static final String FILE_INPUT_XPATH = "//input[@type='file']";
        public static final String DIALOG_CON_CSS = "dialog-con";
    }

    private static final List<String> deadStatus = List.of("半年前活跃");

    public JobService(WebDriverWait wait, BossConfig config, List<Job> resultList, FilterService filterService) {
        this.wait = wait;
        this.config = config;
        this.resultList = resultList;
        this.filterService = filterService;
        this.submittedJobs = new HashSet<>();
        this.noJobPages = 0;
        loadSubmittedJobs();
    }

    /**
     * 主方法：协调多城市、多关键词搜索，收集职位链接并处理
     */
    public void postJobByCity(String s) throws InterruptedException {
        JavascriptExecutor jse = (JavascriptExecutor) CHROME_DRIVER;

        // 访问首页
        log.info("访问Boss直聘首页...");
        CHROME_DRIVER.get("https://www.zhipin.com");
        SeleniumUtil.sleep(1);
        wait.until(driver -> jse.executeScript("return document.readyState").equals("complete"));
        log.info("首页加载完成");

        // 清空 job_links.txt 文件
        try {
            Files.write(Paths.get("job_links.txt"), new byte[0]);
            log.info("已清空 job_links.txt 文件");
        } catch (IOException e) {
            log.error("无法清空 job_links.txt 文件：{}", e.getMessage());
        }

        // 记录当前窗口句柄
        String originalWindow = CHROME_DRIVER.getWindowHandle();
        log.info("当前窗口句柄: {}", originalWindow);

        // 多城市、多关键词搜索并收集链接
        for (String cityCode : config.getCityCode()) {
            for (String keyword : config.getKeywords()) {
                noJobPages = 0;
                log.info("开始处理城市【{}】，关键词【{}】", cityCode, keyword);
                List<String> jobLinks = collectJobLinks(cityCode, keyword, originalWindow);
                if (jobLinks.isEmpty()) {
                    log.warn("城市【{}】关键词【{}】未收集到任何职位链接，跳过", cityCode, keyword);
                    continue;
                }
                writeJobLinksToFile(jobLinks);
            }
        }

        log.info("所有城市和关键词的职位链接收集完成");
        processJobLinks(originalWindow);
    }

    private List<String> collectJobLinks(String cityCode, String keyword, String originalWindow) {
        List<String> jobLinks = new ArrayList<>();
        String searchUrl = buildSearchUrl(cityCode, keyword);
        log.info("构造的搜索 URL: {}", searchUrl);

        // 在原标签页（登录页面）加载 searchUrl
        if (!navigateToSearchPage(searchUrl)) {
            log.warn("导航到搜索页面失败，URL: {}", searchUrl);
            return jobLinks;
        }

        // 等待页面刷新并稳定（在原标签页）
        if (!waitForPageStable()) {
            log.warn("原标签页未能在规定时间内稳定（可能仍在刷新或被爬虫检查拦截），URL: {}", CHROME_DRIVER.getCurrentUrl());
            return jobLinks;
        }

        // 获取当前 URL（原标签页的 URL）
        String currentUrl = CHROME_DRIVER.getCurrentUrl();
        log.info("原标签页稳定后的 URL: {}", currentUrl);

        // 在右侧打开新标签页，复制当前 URL
        String newWindow = SeleniumHelper.openNewTab(CHROME_DRIVER, currentUrl, originalWindow);
        if (newWindow == null) {
            log.error("无法打开新标签页，URL: {}，停止操作", currentUrl);
            return jobLinks;
        }

        // 等待新标签页加载
        if (!SeleniumHelper.waitForPageLoad(CHROME_DRIVER, wait)) {
            log.error("新标签页加载失败，URL: {}，关闭新标签页", CHROME_DRIVER.getCurrentUrl());
            SeleniumHelper.closeTab(CHROME_DRIVER, newWindow, originalWindow);
            return jobLinks;
        }

        // 等待新标签页稳定
        if (!waitForPageStable()) {
            log.warn("新标签页未能在规定时间内稳定，URL: {}，关闭新标签页", CHROME_DRIVER.getCurrentUrl());
            SeleniumHelper.closeTab(CHROME_DRIVER, newWindow, originalWindow);
            return jobLinks;
        }


        // 在新标签页加载更多职位
        if (!loadMoreJobs(currentUrl)) {
            log.warn("在新标签页加载更多职位失败，关闭新标签页");
            SeleniumHelper.closeTab(CHROME_DRIVER, newWindow, originalWindow);
            return jobLinks;
        }

        // 在新标签页滑动到页面底部
        WebElement footer = scrollToFooter(currentUrl);
        if (footer == null) {
            log.warn("在新标签页滑动到 footer 失败，URL: {}，等待页面稳定", CHROME_DRIVER.getCurrentUrl());
            // 如果滑动失败，等待页面稳定
            if (!waitForPageStable()) {
                log.warn("新标签页未能在规定时间内稳定，URL: {}，关闭新标签页", CHROME_DRIVER.getCurrentUrl());
                SeleniumHelper.closeTab(CHROME_DRIVER, newWindow, originalWindow);
                return jobLinks;
            }
            // 页面稳定后重新滑动
            footer = scrollToFooter(currentUrl);
            if (footer == null) {
                log.error("新标签页页面稳定后仍无法滑动到 footer，URL: {}，关闭新标签页", CHROME_DRIVER.getCurrentUrl());
                SeleniumHelper.closeTab(CHROME_DRIVER, newWindow, originalWindow);
                return jobLinks;
            }
        }

        // 在新标签页滑动到 footer 确保可见
        if (!scrollToFooterPosition(footer, currentUrl)) {
            log.warn("在新标签页将 footer 滚动到可视区域失败，URL: {}，等待页面稳定", CHROME_DRIVER.getCurrentUrl());
            // 如果滑动失败，等待页面稳定
            if (!waitForPageStable()) {
                log.warn("新标签页未能在规定时间内稳定，URL: {}，关闭新标签页", CHROME_DRIVER.getCurrentUrl());
                SeleniumHelper.closeTab(CHROME_DRIVER, newWindow, originalWindow);
                return jobLinks;
            }
            // 页面稳定后重新滑动
            footer = scrollToFooter(currentUrl);
            if (footer == null) {
                log.error("新标签页页面稳定后仍无法滑动到 footer，URL: {}，关闭新标签页", CHROME_DRIVER.getCurrentUrl());
                SeleniumHelper.closeTab(CHROME_DRIVER, newWindow, originalWindow);
                return jobLinks;
            }
            if (!scrollToFooterPosition(footer, currentUrl)) {
                log.error("新标签页页面稳定后仍无法将 footer 滚动到可视区域，URL: {}，关闭新标签页", CHROME_DRIVER.getCurrentUrl());
                SeleniumHelper.closeTab(CHROME_DRIVER, newWindow, originalWindow);
                return jobLinks;
            }
        }

        // 防止页面跳转和模拟用户行为（在新标签页）
        SeleniumHelper.preventPageNavigation(CHROME_DRIVER);
        SeleniumHelper.simulateUserBehavior(CHROME_DRIVER);

        // 检查安全验证（在新标签页）
        if (SeleniumHelper.isSecurityCheckTriggered(CHROME_DRIVER)) {
            log.warn("操作过程中触发安全验证，URL: {}，关闭新标签页", CHROME_DRIVER.getCurrentUrl());
            SeleniumHelper.closeTab(CHROME_DRIVER, newWindow, originalWindow);
            return jobLinks;
        }

        // 提取职位链接（在新标签页）
        jobLinks.addAll(extractJobLinks());
        log.info("共提取到 {} 个职位链接", jobLinks.size());

        // 关闭新标签页，保留原标签页（登录页面）
        SeleniumHelper.closeTab(CHROME_DRIVER, newWindow, originalWindow);

        return jobLinks;
    }

    /**
     * 等待页面刷新完成，确保页面稳定（不再刷新）
     * @return 如果页面稳定，返回 true；否则返回 false
     */
    private boolean waitForPageStable() {
        // 设置最大等待时间（例如 30 秒）
        long timeoutSeconds = 10;
        long startTime = System.currentTimeMillis();
        String previousUrl = CHROME_DRIVER.getCurrentUrl();
        String previousPageSource = CHROME_DRIVER.getPageSource();
        int stableCount = 0;
        final int requiredStableCount = 7; // 连续 3 次检查 URL 和页面内容未变化，认为页面稳定

        log.info("开始等待页面稳定，初始 URL: {}", previousUrl);

        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000) {
            // 等待 1 秒
            SeleniumUtil.sleep(1);

            // 获取当前 URL 和页面内容
            String currentUrl = CHROME_DRIVER.getCurrentUrl();
            String currentPageSource = CHROME_DRIVER.getPageSource();

            // 检查 URL 和页面内容是否发生变化
            if (currentUrl.equals(previousUrl) && currentPageSource.equals(previousPageSource)) {
                stableCount++;
                log.debug("页面稳定检查，连续未变化次数: {}", stableCount);
                if (stableCount >= requiredStableCount) {
                    log.info("页面已稳定，URL: {}", currentUrl);
                    return true;
                }
            } else {
                // 如果页面发生变化，重置计数器
                stableCount = 0;
                log.debug("页面发生变化，URL 从 {} 变为 {}，重置稳定计数", previousUrl, currentUrl);
            }

            // 更新比较基准
            previousUrl = currentUrl;
            previousPageSource = currentPageSource;

            // 检查是否触发安全验证
            if (SeleniumHelper.isSecurityCheckTriggered(CHROME_DRIVER)) {
                log.warn("等待页面稳定过程中触发安全验证，URL: {}", currentUrl);
                return false;
            }
        }

        log.warn("页面未能在 {} 秒内稳定，可能是爬虫检查导致的多次刷新，URL: {}", timeoutSeconds, CHROME_DRIVER.getCurrentUrl());
        return false;
    }

    /**
     * 构造搜索 URL
     */
    private String buildSearchUrl(String cityCode, String keyword) {
        return BASE_URL +
                JobUtils.appendParam("city", cityCode) +
                JobUtils.appendParam("jobType", config.getJobType()) +
                JobUtils.appendParam("salary", config.getSalary()) +
                JobUtils.appendListParam("experience", config.getExperience()) +
                JobUtils.appendListParam("degree", config.getDegree()) +
                JobUtils.appendListParam("scale", config.getScale()) +
                JobUtils.appendListParam("industry", config.getIndustry()) +
                JobUtils.appendListParam("stage", config.getStage()) +
                "&query=" + keyword;
    }

    /**
     * 导航到搜索页面并处理安全验证
     */
    private boolean navigateToSearchPage(String searchUrl) {
        try {
            CHROME_DRIVER.get(searchUrl);
            log.info("已导航到搜索结果页面");
        } catch (Exception e) {
            log.error("导航到搜索结果页面失败：{}", e.getMessage());
            return false;
        }

        try {
            log.info("等待搜索结果页面加载...");
            SeleniumUtil.sleep(1);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(@class, 'job-list')]")));

            log.info("搜索结果页面加载完成");
        } catch (Exception e) {
            log.error("搜索结果页面加载失败：{}", e.getMessage());
            return false;
        }

        String currentUrl = CHROME_DRIVER.getCurrentUrl();
        if (currentUrl.contains("security-check.html")) {
            log.warn("搜索后触发安全验证，URL：{}，暂停以便观察...", currentUrl);
            SeleniumUtil.sleep(30);
            currentUrl = CHROME_DRIVER.getCurrentUrl();
            if (currentUrl.contains("security-check.html")) {
                log.warn("安全验证后未跳回搜索页面，当前URL: {}，程序暂停...", currentUrl);
                SeleniumUtil.sleep(60);
                return false;
            }
            log.info("安全验证后跳回搜索页面，继续收集...");
        }
        return true;
    }

    /**
     * 移除 URL 中的 page 参数
     */
    private String removePageParam(String currentUrl) {
        String modifiedUrl = currentUrl;
        try {
            if (modifiedUrl.contains("page=")) {
                modifiedUrl = modifiedUrl.replaceAll("page=\\d+(&|$)", "");
                modifiedUrl = modifiedUrl.endsWith("&") ? modifiedUrl.substring(0, modifiedUrl.length() - 1) : modifiedUrl;
                log.info("已移除 page 参数，调整后的 URL: {}", modifiedUrl);
            } else {
                log.info("URL 中不含 page 参数，无需调整: {}", modifiedUrl);
            }
            return modifiedUrl;
        } catch (Exception e) {
            log.error("处理 URL 时失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 加载更多职位
     */
    private boolean loadMoreJobs(String modifiedUrl) {
        SeleniumUtil.sleep(5);
        log.info("在新标签页中尝试加载更多职位...");
        JavascriptExecutor jse = (JavascriptExecutor) CHROME_DRIVER;
        int maxLoadAttempts = 100000;
        int loadAttempt = 0;
        int previousJobCount = 0;
        int currentJobCount = 0;

        while (loadAttempt < maxLoadAttempts) {
            try {
                List<WebElement> jobCards = CHROME_DRIVER.findElements(By.cssSelector("div.job-card-wrapper"));
                currentJobCount = jobCards.size();
                log.info("当前职位数量: {}", currentJobCount);

                if (loadAttempt > 0 && currentJobCount == previousJobCount) {
                    log.warn("职位数量未增加，停止加载...");
                    break;
                }

                jse.executeScript("var wheelEvent = new WheelEvent('wheel', { deltaY: 500 }); document.querySelector('body').dispatchEvent(wheelEvent);");
                log.info("模拟用户滚动，尝试加载更多职位，第 {} 次", loadAttempt + 1);

                long scrollPosition = (Long) jse.executeScript("return window.scrollY;");
                jse.executeScript("window.scrollTo({ top: arguments[0], behavior: 'smooth' });", scrollPosition + 1000);
                log.info("逐步滚动到位置: {}", scrollPosition + 1000);
                SeleniumUtil.sleep(2);

                String tempUrl = CHROME_DRIVER.getCurrentUrl();
                if (!tempUrl.equals(modifiedUrl)) {
                    log.warn("加载更多职位过程中 URL 变化，当前 URL: {}，尝试恢复...", tempUrl);
                    CHROME_DRIVER.get(modifiedUrl);
                    SeleniumUtil.sleep(1);
                    continue;
                }

                previousJobCount = currentJobCount;
                loadAttempt++;
            } catch (Exception e) {
                log.warn("加载更多职位失败：{}", e.getMessage());
                loadAttempt++;
                SeleniumUtil.sleep(1);
            }
        }
        return true;
    }

    /**
     * 滚动到页面底部 footer
     */
    private WebElement scrollToFooter(String modifiedUrl) {
        JavascriptExecutor jse = (JavascriptExecutor) CHROME_DRIVER;
        log.info("在新标签页中滚动到页面底部 footer...");
        boolean footerLoaded = false;
        int maxScrollAttempts = 100000;
        int scrollAttempt = 0;
        long step = 100000;
        long currentPosition = 0;

        while (!footerLoaded && scrollAttempt < maxScrollAttempts) {
            try {
                jse.executeScript("window.scrollTo({ top: arguments[0], behavior: 'smooth' });", currentPosition);
                log.info("逐步滚动到位置: {}", currentPosition);
                SeleniumUtil.sleep(1);

                WebElement footer = CHROME_DRIVER.findElement(By.cssSelector("#footer"));
                if (footer != null && footer.isDisplayed()) {
                    log.info("footer 已加载到 DOM 并可见");
                    footerLoaded = true;
                    return footer;
                }

                currentPosition += step;
                scrollAttempt++;
            } catch (Exception e) {
                log.info("footer 尚未加载，继续滚动... 当前尝试次数: {}/{}", scrollAttempt + 1, maxScrollAttempts);
                currentPosition += step;
                scrollAttempt++;
                SeleniumUtil.sleep(1);
            }
        }

        log.error("尝试 {} 次后仍未加载 footer，跳过当前关键词", maxScrollAttempts);
        return null;
    }

    /**
     * 滚动到 footer 位置并确保可见
     */
    private boolean scrollToFooterPosition(WebElement footer, String modifiedUrl) {
        JavascriptExecutor jse = (JavascriptExecutor) CHROME_DRIVER;
        Long footerPosition = SeleniumHelper.getElementPosition(jse, footer);
        if (footerPosition == null) {
            return false;
        }

        Long pageHeight = SeleniumHelper.getPageHeight(jse);
        if (pageHeight == null) {
            return false;
        }

        int maxRetries = 5;
        int retryCount = 0;
        boolean isFooterInViewport = false;
        long step = 100000;
        long safeStopPosition = Math.max(0, pageHeight - 1000);
        long currentPosition = (Long) jse.executeScript("return window.scrollY;");

        while (currentPosition < safeStopPosition && currentPosition < footerPosition) {
            try {
                jse.executeScript("window.scrollTo({ top: arguments[0], behavior: 'smooth' });", currentPosition);
                log.info("逐步滚动到位置: {}", currentPosition);
                SeleniumUtil.sleep(3);

                String tempUrl = CHROME_DRIVER.getCurrentUrl();
                if (!tempUrl.equals(modifiedUrl)) {
                    log.warn("逐步滚动过程中 URL 变化，当前 URL: {}，尝试恢复...", tempUrl);
                    CHROME_DRIVER.get(modifiedUrl);
                    SeleniumUtil.sleep(1);
                    footer = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#footer")));
                    footerPosition = SeleniumHelper.getElementPosition(jse, footer);
                    if (footerPosition == null) {
                        return false;
                    }
                    log.info("重新定位 footer 后，footer 绝对位置: {}", footerPosition);
                    continue;
                }

                currentPosition += step;
            } catch (Exception e) {
                log.warn("逐步滚动失败：{}", e.getMessage());
                SeleniumUtil.sleep(1);
                continue;
            }
        }

        while (retryCount < maxRetries && !isFooterInViewport) {
            try {
                jse.executeScript("arguments[0].scrollIntoView({ behavior: 'smooth', block: 'start' });", footer);
                log.info("尝试平滑滚动到页面底部 footer，第 {} 次", retryCount + 1);
                SeleniumUtil.sleep(1);

                String script = "var rect = arguments[0].getBoundingClientRect(); var windowHeight = window.innerHeight; return rect.top >= 0 && rect.top <= windowHeight;";
                isFooterInViewport = (Boolean) jse.executeScript(script, footer);
                log.info("footer 是否在视口中: {}", isFooterInViewport);

                String tempUrl = CHROME_DRIVER.getCurrentUrl();
                if (!tempUrl.equals(modifiedUrl)) {
                    log.warn("滚动到 footer 过程中 URL 变化，当前 URL: {}，尝试恢复...", tempUrl);
                    CHROME_DRIVER.get(modifiedUrl);
                    SeleniumUtil.sleep(1);
                    footer = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#footer")));
                    footerPosition = SeleniumHelper.getElementPosition(jse, footer);
                    if (footerPosition == null) {
                        return false;
                    }
                    log.info("重新定位 footer 后，footer 绝对位置: {}", footerPosition);
                    retryCount++;
                    continue;
                }

                if (!isFooterInViewport) {
                    Long scrollY = (Long) jse.executeScript("return window.scrollY;");
                    log.info("当前滚动位置 (scrollY): {}", scrollY);
                    if (scrollY == 0) {
                        log.warn("检测到页面可能已刷新，滚动位置回到顶部，重新滚动...");
                    }
                    retryCount++;
                    continue;
                }

                log.info("已成功滚动到页面底部 footer");
                return true;
            } catch (Exception e) {
                log.error("滚动到 footer 失败：{}", e.getMessage());
                retryCount++;
                SeleniumUtil.sleep(1);
            }
        }

        log.error("重试 {} 次后仍无法滚动到 footer", maxRetries);
        return false;
    }

    /**
     * 提取职位链接
     */
    private List<String> extractJobLinks() {
        List<String> jobLinks = new ArrayList<>();
        List<WebElement> jobCards = new ArrayList<>();

        try {
            WebElement jobListContainer = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(Elements.JOB_LIST_XPATH)));
            log.info("成功定位职位列表容器");

            int cardIndex = 1;
            while (true) {
                String jobCardXpath = Elements.JOB_LIST_XPATH + "/div[" + cardIndex + "]";
                try {
                    WebElement jobCard = CHROME_DRIVER.findElement(By.xpath(jobCardXpath));
                    jobCards.add(jobCard);
                    log.info("找到职位卡片 {}: XPath: {}", cardIndex, jobCardXpath);
                    cardIndex++;
                } catch (Exception e) {
                    log.info("没有更多职位卡片，停止查找，总共找到 {} 个卡片", jobCards.size());
                    break;
                }
            }

            for (int i = 0; i < jobCards.size(); i++) {
                WebElement jobCard = jobCards.get(i);
                try {
                    String jobCardHtml = jobCard.getAttribute("outerHTML");
                    log.info("职位卡片 {} HTML 内容：\n{}", i + 1, jobCardHtml);
                } catch (Exception e) {
                    log.warn("无法获取职位卡片 {} 的 HTML 内容：{}", i + 1, e.getMessage());
                }
            }

            for (int i = 0; i < jobCards.size(); i++) {
                WebElement jobCard = jobCards.get(i);
                try {
                    String jobHref = null;
                    try {
                        WebElement linkElement = jobCard.findElement(By.cssSelector("a.job-card-left"));
                        jobHref = linkElement.getAttribute("href");
                    } catch (Exception e1) {
                        log.warn("选择器 a.job-card-left 无法定位链接，尝试其他选择器...");
                        try {
                            WebElement linkElement = jobCard.findElement(By.xpath(".//a[@href]"));
                            jobHref = linkElement.getAttribute("href");
                        } catch (Exception e2) {
                            log.warn("选择器 .//a[@href] 也无法定位链接，打印卡片 HTML 以调试...");
                            log.info("职位卡片 {} HTML 内容：\n{}", i + 1, jobCard.getAttribute("outerHTML"));
                            continue;
                        }
                    }

                    if (jobHref == null || jobHref.isEmpty() || !jobHref.startsWith("http")) {
                        log.warn("职位卡片 {} 的链接无效: {}", i + 1, jobHref);
                        continue;
                    }

                    log.info("职位卡片 {} 的链接: {}", i + 1, jobHref);
                    jobLinks.add(jobHref);
                } catch (Exception e) {
                    log.warn("无法提取职位卡片 {} 的链接：{}", i + 1, e.getMessage());
                }
            }
            log.info("共提取到 {} 个职位链接", jobLinks.size());
        } catch (Exception e) {
            log.error("获取职位卡片信息失败：{}", e.getMessage());
        }
        return jobLinks;
    }

    /**
     * 将职位链接追加写入文件
     */
    private void writeJobLinksToFile(List<String> jobLinks) {
        try (FileWriter writer = new FileWriter("job_links.txt", true)) {
            for (int i = 0; i < jobLinks.size(); i++) {
                writer.write("职位卡片 " + (i + 1) + ": " + jobLinks.get(i) + "\n");
            }
            writer.flush();
            log.info("成功追加写入 {} 个职位链接到 job_links.txt", jobLinks.size());
        } catch (IOException e) {
            log.error("写入 job_links.txt 失败：{}", e.getMessage());
        }
    }

    /**
     * 处理职位链接
     */
    private void processJobLinks(String originalWindow) throws InterruptedException {
        JavascriptExecutor jse = (JavascriptExecutor) CHROME_DRIVER;
        List<String> jobLinks = readJobLinksFromFile();
        if (jobLinks.isEmpty()) {
            log.warn("job_links.txt 中没有有效的职位链接，跳过处理");
            return;
        }

        for (int i = 0; i < jobLinks.size(); i++) {
            if (successfulSubmissions >= MAX_SUCCESSFUL_SUBMISSIONS) {
                log.info("已成功投递 {} 个职位，达到上限，终止处理", successfulSubmissions);
                break;
            }

            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                log.warn("连续投递失败 {} 次，达到上限，终止处理", consecutiveErrors);
                break;
            }

            String jobHref = jobLinks.get(i);
            log.info("处理职位链接 {}: {}", i + 1, jobHref);

            String newWindow = SeleniumHelper.openNewTab(CHROME_DRIVER, jobHref, originalWindow);
            if (newWindow == null) {
                continue;
            }

            if (!SeleniumHelper.waitForPageLoad(CHROME_DRIVER, wait)) {
                SeleniumHelper.closeTab(CHROME_DRIVER, newWindow, originalWindow);
                continue;
            }

            if (SeleniumHelper.isSecurityCheckTriggered(CHROME_DRIVER)) {
                SeleniumHelper.closeTab(CHROME_DRIVER, newWindow, originalWindow);
                continue;
            }

            if (isDeadHR()) {
                log.info("HR 不活跃，根据配置跳过此职位：{}", jobHref);
                SeleniumHelper.closeTab(CHROME_DRIVER, newWindow, originalWindow);
                continue;
            }

            Job job = extractJobDetails(jobHref);
            if (!applyForJob(job, jobHref)) {
                consecutiveErrors++;
            }

            SeleniumHelper.closeTab(CHROME_DRIVER, newWindow, originalWindow);
        }

        log.info("所有职位链接处理完成，共处理 {} 个职位", resultList.size());
    }

    /**
     * 从文件读取职位链接
     */
    private List<String> readJobLinksFromFile() {
        List<String> jobLinks = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(Paths.get("job_links.txt"));
            for (String line : lines) {
                if (line.contains(":")) {
                    String[] parts = line.split(": ");
                    if (parts.length > 1 && parts[1].startsWith("http")) {
                        String jobHref = parts[1].trim();
                        if (!submittedJobs.contains(jobHref)) {
                            jobLinks.add(jobHref);
                        } else {
                            log.info("职位已投递，排除：{}", jobHref);
                        }
                    }
                }
            }
            log.info("从 job_links.txt 读取到 {} 个待处理职位链接（排除已投递后）", jobLinks.size());
        } catch (IOException e) {
            log.error("读取 job_links.txt 失败：{}", e.getMessage());
        }
        return jobLinks;
    }

    /**
     * 提取职位详细信息
     */
    private Job extractJobDetails(String jobHref) {
        Job job = new Job();
        job.setHref(jobHref);

        try {
            WebElement jobNameElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"main\"]/div[1]/div/div/div[1]/div[2]/h1")));
            job.setJobName(jobNameElement.getText());
            log.info("提取职位名称: {}", job.getJobName());
        } catch (Exception e) {
            log.warn("无法提取职位名称：{}", e.getMessage());
            job.setJobName("未知职位");
        }

        try {
            WebElement salaryElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"main\"]/div[1]/div/div/div[1]/div[2]/span")));
            job.setSalary(salaryElement.getText());
            log.info("提取薪资: {}", job.getSalary());
        } catch (Exception e) {
            log.warn("无法提取薪资：{}", e.getMessage());
            job.setSalary("未知薪资");
        }

        try {
            WebElement companyElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"main\"]/div[3]/div/div[1]/div[2]/div/a[2]")));
            job.setCompanyName(companyElement.getText());
            log.info("提取公司名称: {}", job.getCompanyName());
        } catch (Exception e) {
            log.warn("无法提取公司名称：{}", e.getMessage());
            job.setCompanyName("未知公司");
        }

        String recruiter = "未知招聘者";
        try {
            WebElement recruiterNameElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#main > div.job-box > div > div.job-detail > div:nth-child(1) > div.job-boss-info > h2")));
            String recruiterName = recruiterNameElement.getText().replaceAll("<[^>]+>", "").trim();
            WebElement recruiterTitleElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#main > div.job-box > div > div.job-detail > div:nth-child(1) > div.job-boss-info > div.boss-info-attr")));
            String recruiterTitle = recruiterTitleElement.getText().replaceAll("<[^>]+>", "").trim();
            recruiter = recruiterName + " " + recruiterTitle;
            job.setRecruiter(recruiter);
            log.info("提取招聘者信息: {}", recruiter);
        } catch (Exception e) {
            log.warn("无法提取招聘者信息：{}", e.getMessage());
            job.setRecruiter(recruiter);
        }

        try {
            WebElement cityElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"main\"]/div[1]/div/div/div[1]/p/a")));
            job.setJobArea(cityElement.getText());
            log.info("提取职位区域: {}", job.getJobArea());
        } catch (Exception e) {
            log.warn("无法提取职位区域：{}", e.getMessage());
            job.setJobArea("未知区域");
        }

        try {
            StringBuilder tags = new StringBuilder();
            List<WebElement> tagElements = CHROME_DRIVER.findElements(By.cssSelector("#main > div.job-banner > div > div > div.tag-container-new > div.job-tags"));
            for (WebElement tagElement : tagElements) {
                tags.append(tagElement.getText()).append("·");
            }
            String jobTags = tags.length() > 0 ? tags.substring(0, tags.length() - 1) : "";
            job.setCompanyTag(jobTags);
            log.info("提取职位标签: {}", jobTags);
        } catch (Exception e) {
            log.warn("无法提取职位标签：{}", e.getMessage());
            job.setCompanyTag("");
        }

        return job;
    }

    /**
     * 投递职位
     */
    private boolean applyForJob(Job job, String jobHref) {
        JavascriptExecutor jse = (JavascriptExecutor) CHROME_DRIVER;
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#main > div.job-banner > div > div > div.info-primary > div.job-op > div.btn-container > div > a")));
            WebElement btn = CHROME_DRIVER.findElement(By.cssSelector("#main > div.job-banner > div > div > div.info-primary > div.job-op > div.btn-container > div > a"));
            if (!"立即沟通".equals(btn.getText())) {
                log.info("无法投递，按钮状态异常：{}", btn.getText());
                return false;
            }

            SeleniumUtil.sleep(1);
//            int sleepTime = parseSleepTime(config.getWaitTime());
//            SeleniumUtil.sleep(sleepTime);

            AiFilter filterResult = null;
            if (config.getEnableAI()) {
                String jd = CHROME_DRIVER.findElement(By.xpath("//div[@class='job-sec-text']")).getText();
                filterResult = checkJob("", job.getJobName(), jd);
            }

            btn.click();
            SeleniumUtil.sleep(1);

            try {
                WebElement closeIcon = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("/html/body/div[12]/div[2]/div[1]/a/i")));
                closeIcon.click();
                log.info("身份验证窗口已关闭");
                btn.click();
                log.info("再次点击‘立即沟通’按钮");
                SeleniumUtil.sleep(1);
            } catch (Exception e) {
                log.debug("未检测到身份验证窗口，继续投递流程");
            }

            if (isLimit()) {
                log.warn("今日沟通次数已达上限，停止投递");
                return false;
            }

            if (hasSentMessage()) {
                log.info("已发送消息，跳过投递：{}", jobHref);
                return true;
            }

            try {
                WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#chat-input")));
                if (!input.isDisplayed()) {
                    log.warn("聊天输入框不可见，尝试通过 JavaScript 激活");
                    jse.executeScript("arguments[0].style.display='block'; arguments[0].focus();", input);
                }
                input.click();

                String message = (filterResult != null && filterResult.getResult() && isValidString(filterResult.getMessage()))
                        ? filterResult.getMessage()
                        : config.getSayHi();
                input.sendKeys(message);
                WebElement send = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("#container > div > div > div.chat-conversation > div.message-controls > div > div:nth-child(2) > div.chat-op > button")));
                send.click();
                SeleniumUtil.sleep(1);

                if (hasSentMessage()) {
                    log.info("消息发送成功：{}", message);
                    String company = job.getCompanyName() != null ? job.getCompanyName() : "未知公司: " + job.getHref();
                    Boolean imgResume = sendResume(company);
                    SeleniumUtil.sleep(1);
                    String position = job.getJobName() + " " + job.getSalary() + " " + job.getJobArea();
                    log.info("投递成功【{}】公司，【{}】职位，招聘官:【{}】{}",
                            company, position, job.getRecruiter(), imgResume ? "发送图片简历成功！" : "");
                    submittedJobs.add(jobHref);
                    saveSubmittedJob(jobHref);
                    resultList.add(job);
                    successfulSubmissions++;
                    consecutiveErrors = 0;
                    return true;
                } else {
                    log.error("消息发送失败，未在聊天记录中找到自己的消息：{}", message);
                    return false;
                }
            } catch (Exception e) {
                log.error("发送消息失败: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("投递失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 解析等待时间
     */
    private int parseSleepTime(String waitTime) {
        int sleepTime = 1;
        if (waitTime != null) {
            try {
                sleepTime = Integer.parseInt(waitTime);
            } catch (NumberFormatException e) {
                log.error("等待时间转换异常！！");
            }
        }
        return sleepTime;
    }

    @SneakyThrows
    public Boolean sendResume(String company) {
        if (!config.getSendImgResume()) {
            log.info("未启用图片简历功能，跳过发送");
            return false;
        }
        try {
            log.info("尝试加载简历图片文件...");
            URL resourceUrl = JobService.class.getResource("/resume.jpg");
            if (resourceUrl == null) {
                log.error("在类路径下未找到 resume.jpg 文件！");
                return false;
            }
            File imageFile = new File(resourceUrl.toURI());
            log.info("简历图片路径：{}", imageFile.getAbsolutePath());

            if (!imageFile.exists()) {
                log.error("简历图片不存在！: {}", imageFile.getAbsolutePath());
                return false;
            }
            log.info("定位文件上传输入框...");
            WebElement fileInput = CHROME_DRIVER.findElement(By.xpath(Elements.FILE_INPUT_XPATH));
            fileInput.sendKeys(imageFile.getAbsolutePath());
            log.info("成功上传简历图片");
            return true;
        } catch (Exception e) {
            log.error("发送简历图片时出错：{}", e.getMessage(), e);
            return false;
        }
    }

    private boolean isLimit() {
        try {
            SeleniumUtil.sleep(1);
            String text = CHROME_DRIVER.findElement(By.className(Elements.DIALOG_CON_CSS)).getText();
            log.info("检查投递上限，对话框内容: {}", text);
            return text.contains("已达上限");
        } catch (Exception e) {
            log.debug("未检测到投递上限提示");
            return false;
        }
    }

    private boolean isValidString(String str) {
        boolean isValid = str != null && !str.isEmpty();
        log.debug("检查字符串有效性: {}, 结果: {}", str, isValid);
        return isValid;
    }

    private boolean isDeadHR() {
        if (!config.getFilterDeadHR()) {
            return false;
        }
        try {
            String activeTimeText = CHROME_DRIVER.findElement(By.xpath("//span[@class='boss-active-time']")).getText();
            log.info("{}：{}", filterService.getCompanyAndHR(), activeTimeText);
            return containsDeadStatus(activeTimeText, deadStatus);
        } catch (Exception e) {
            log.info("没有找到【{}】的活跃状态, 默认此岗位将会投递...", filterService.getCompanyAndHR());
            return false;
        }
    }

    public static boolean containsDeadStatus(String activeTimeText, List<String> deadStatus) {
        for (String status : deadStatus) {
            if (activeTimeText.contains(status)) {
                return true;
            }
        }
        return false;
    }

    private AiFilter checkJob(String keyword, String jobName, String jd) {
        AiConfig aiConfig = AiConfig.init();
        String requestMessage = String.format(aiConfig.getPrompt(), aiConfig.getIntroduce(), keyword, jobName, jd, config.getSayHi());
        String result = AiService.sendRequest(requestMessage);
        return result.contains("false") ? new AiFilter(false) : new AiFilter(true, result);
    }

    private void loadSubmittedJobs() {
        try {
            if (Files.exists(Paths.get("submitted_jobs.txt"))) {
                List<String> lines = Files.readAllLines(Paths.get("submitted_jobs.txt"));
                submittedJobs.addAll(lines);
                log.info("从 submitted_jobs.txt 加载到 {} 个已投递职位链接", submittedJobs.size());
            } else {
                log.info("submitted_jobs.txt 文件不存在，将创建新文件");
            }
        } catch (IOException e) {
            log.error("读取 submitted_jobs.txt 失败：{}", e.getMessage());
        }
    }

    private void saveSubmittedJob(String jobHref) {
        try (FileWriter writer = new FileWriter("submitted_jobs.txt", true)) {
            writer.write(jobHref + "\n");
            writer.flush();
            log.info("已将职位链接写入 submitted_jobs.txt: {}", jobHref);
        } catch (IOException e) {
            log.error("写入 submitted_jobs.txt 失败：{}", e.getMessage());
        }
    }

    private boolean hasSentMessage() {
        try {
            WebElement chatConversation = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("ul.im-list")));
            log.debug("成功定位聊天记录容器：ul.im-list");
            List<WebElement> selfMessages = chatConversation.findElements(By.cssSelector("li.message-item.item-myself"));
            if (!selfMessages.isEmpty()) {
                log.info("找到自己发送的消息，消息数量：{}", selfMessages.size());
                for (WebElement message : selfMessages) {
                    log.debug("自己发送的消息内容：{}", message.getText());
                }
                return true;
            } else {
                log.info("未找到自己发送的消息");
                return false;
            }
        } catch (Exception e) {
            log.info("检查聊天记录失败，可能是新对话窗口或页面未加载：{}", e.getMessage());
            return false;
        }
    }
}

/**
 * Selenium 辅助工具类
 */
class SeleniumHelper {
    private static final Logger log = LoggerFactory.getLogger(SeleniumHelper.class);

    public static String openNewTab(WebDriver driver, String url, String originalWindow) {
        JavascriptExecutor jse = (JavascriptExecutor) driver;
        try {
            jse.executeScript("window.open('" + url + "', '_blank');");
            log.info("已在新标签页打开 URL: {}", url);
            SeleniumUtil.sleep(5);
            for (String windowHandle : driver.getWindowHandles()) {
                if (!windowHandle.equals(originalWindow)) {
                    driver.switchTo().window(windowHandle);
                    log.info("已切换到新标签页，窗口句柄: {}", windowHandle);
                    return windowHandle;
                }
            }
        } catch (Exception e) {
            log.error("无法打开或切换到新标签页：{}", e.getMessage());
        }
        log.error("无法切换到新标签页");
        return null;
    }

    public static void closeTab(WebDriver driver, String newWindow, String originalWindow) {
        try {
            driver.switchTo().window(newWindow);
            driver.navigate().refresh();
            log.info("在新标签页中刷新页面...");
            SeleniumUtil.sleep(1);
        } catch (Exception e) {
            log.warn("刷新页面失败：{}", e.getMessage());
        }

        try {
            driver.close();
            driver.switchTo().window(originalWindow);
            log.info("已关闭新标签页，切换回原标签页，窗口句柄: {}", originalWindow);
        } catch (Exception e) {
            log.error("关闭新标签页失败：{}", e.getMessage());
            for (String windowHandle : driver.getWindowHandles()) {
                if (!windowHandle.equals(originalWindow)) {
                    driver.switchTo().window(windowHandle);
                    driver.close();
                }
            }
            driver.switchTo().window(originalWindow);
        }
    }

    public static boolean waitForPageLoad(WebDriver driver, WebDriverWait wait) {
        JavascriptExecutor jse = (JavascriptExecutor) driver;
        try {
            wait.until(driver1 -> jse.executeScript("return document.readyState").equals("complete"));
            log.info("页面加载完成，URL: {}", driver.getCurrentUrl());
            return true;
        } catch (Exception e) {
            log.error("页面加载失败：{}", e.getMessage());
            return false;
        }
    }

    public static boolean isSecurityCheckTriggered(WebDriver driver) {
        String currentUrl = driver.getCurrentUrl();
        if (currentUrl.contains("security-check.html")) {
            log.warn("触发安全验证，URL：{}，暂停以便观察...", currentUrl);
            SeleniumUtil.sleep(3);
            return true;
        }
        return false;
    }

    public static void preventPageNavigation(WebDriver driver) {
        JavascriptExecutor jse = (JavascriptExecutor) driver;
        try {
            jse.executeScript(
                    "window.addEventListener('popstate', function(e) { e.preventDefault(); history.pushState(null, null, window.location.href); });" +
                            "window.setTimeout = function() { return 0; };" +
                            "window.setInterval = function() { return 0; };"
            );
            log.info("已设置 popstate 监听和禁用定时器，防止页面跳转");
        } catch (Exception e) {
            log.warn("设置跳转拦截失败：{}", e.getMessage());
        }

        try {
            jse.executeScript(
                    "window.fetch = function(url) { " +
                            "  if (url.includes('security-check') || url.includes('captcha')) { " +
                            "    console.log('Blocked fetch request:', url); " +
                            "    return Promise.resolve({ ok: false }); " +
                            "  } " +
                            "  return fetch(url); " +
                            "};" +
                            "XMLHttpRequest.prototype.open = function(method, url) { " +
                            "  if (url.includes('security-check') || url.includes('captcha')) { " +
                            "    console.log('Blocked XHR request:', url); " +
                            "    return; " +
                            "  } " +
                            "  this._open(method, url); " +
                            "};" +
                            "XMLHttpRequest.prototype._open = XMLHttpRequest.prototype.open;" +
                            "XMLHttpRequest.prototype.send = function() {};"
            );
            log.info("已增强 AJAX 请求拦截，防止动态加载触发跳转");
        } catch (Exception e) {
            log.warn("增强 AJAX 请求拦截失败：{}", e.getMessage());
        }
    }

    public static void simulateUserBehavior(WebDriver driver) {
        JavascriptExecutor jse = (JavascriptExecutor) driver;
        try {
            jse.executeScript(
                    "document.querySelector('body').dispatchEvent(new Event('mousemove'));" +
                            "var wheelEvent = new WheelEvent('wheel', { deltaY: 100 });" +
                            "document.querySelector('body').dispatchEvent(wheelEvent);"
            );
            log.info("模拟鼠标移动和滚动事件，降低防爬触发概率");
        } catch (Exception e) {
            log.warn("模拟用户行为失败：{}", e.getMessage());
        }
    }

    public static Long getElementPosition(JavascriptExecutor jse, WebElement element) {
        try {
            Object position = jse.executeScript("return arguments[0].getBoundingClientRect().top + window.scrollY;", element);
            Long footerPosition = position instanceof Double ? ((Double) position).longValue() : (Long) position;
            log.info("元素绝对位置: {}", footerPosition);
            return footerPosition;
        } catch (Exception e) {
            log.error("无法获取元素绝对位置：{}", e.getMessage());
            return null;
        }
    }

    public static Long getPageHeight(JavascriptExecutor jse) {
        try {
            Object height = jse.executeScript("return document.body.scrollHeight;");
            Long pageHeight = height instanceof Double ? ((Double) height).longValue() : (Long) height;
            log.info("页面总高度: {}", pageHeight);
            return pageHeight;
        } catch (Exception e) {
            log.error("无法获取页面总高度：{}", e.getMessage());
            return null;
        }
    }
}
