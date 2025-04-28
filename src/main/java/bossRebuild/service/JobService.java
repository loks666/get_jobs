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
import java.util.Optional;
import java.util.Set;

import static bossRebuild.service.FilterService.containsDeadStatus;
import static utils.Constant.*;

public class JobService {
    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private final WebDriverWait wait;
    private  final BossConfig config;
    private final List<Job> resultList;
    private final FilterService filterService;
    private final Set<String> submittedJobs;
    private int noJobPages;


    // 新增计数器
    private int successfulSubmissions = 0; // 成功投递计数器
    private int consecutiveErrors = 0; // 连续错误计数器
    private static final int MAX_SUCCESSFUL_SUBMISSIONS = 2; // 最大成功投递数量
    private static final int MAX_CONSECUTIVE_ERRORS = 5; // 最大连续错误次数

    private static class Elements {
        public static final String JOB_LIST_XPATH = "//*[@id=\"wrap\"]/div[2]/div[3]/div/div/div[1]/ul";
        public static final String JOB_CARD_CSS = "li.job-card-box";
        public static final String JOB_NAME_CSS = "a.job-name";
        public static final String JOB_SALARY_CSS = "span.job-salary";
        public static final String COMPANY_NAME_CSS = "span.boss-name";
        public static final String JOB_AREA_CSS = "span.company-location";
        public static final String JOB_TAGS_CSS = "ul.tag-list li";
        public static final String JOB_HREF_CSS = "a.job-name";
        public static final String CHAT_BUTTON_CSS = "a.op-btn-chat";
        public static final String RECRUITER_NAME_IN_DETAIL_XPATH = "//h2[contains(@class, 'name')]";
        public static final String RECRUITER_STATUS_XPATH = "//span[contains(@class, 'boss-online-tag')]";
        public static final String JOB_DETAIL_CONTAINER_CSS = "div.job-detail-body";
        public static final String JOB_DESCRIPTION_XPATH = "//p[contains(@class, 'desc')]";
        public static final String ERROR_CONTENT_XPATH = "//div[contains(@class, 'error-content')]";
        public static final String INPUT_AREA_XPATH = "//div[contains(@class, 'chat-input-area')]";
        public static final String DIALOG_TITLE_XPATH = "//div[contains(@class, 'dialog-title')]";
        public static final String CLOSE_ICON_XPATH = "//span[contains(@class, 'close-icon')]";
        public static final String CHAT_INPUT_XPATH = "//textarea[contains(@class, 'chat-input')]";
        public static final String SEND_BUTTON_XPATH = "//button[contains(@class, 'send-btn')]";
        public static final String DIALOG_CONTAINER_XPATH = "//div[contains(@class, 'dialog-container')]";
        public static final String RECRUITER_NAME_XPATH = "//div[contains(@class, 'recruiter-name')]";
        public static final String RECRUITER_TITLE_XPATH = "//div[contains(@class, 'recruiter-title')]";
        public static final String COMPANY_XPATH = "//div[contains(@class, 'company-info')]";
        public static final String POSITION_NAME_XPATH = "//span[contains(@class, 'job-name')]";
        public static final String POSITION_SALARY_XPATH = "//span[contains(@class, 'job-salary')]";
        public static final String POSITION_CITY_XPATH = "//ul[contains(@class, 'tag-list')]//li[1]";
        public static final String FILE_INPUT_XPATH = "//input[@type='file']";
        public static final String DIALOG_CON_CSS = "dialog-con";
    }

    private static final String BASE_URL = "https://www.zhipin.com/web/geek/job?";

    private static final List<String> deadStatus = List.of("半年前活跃"); // 复用 Boss.java 的 deadStatus

    public JobService(WebDriverWait wait, BossConfig config, List<Job> resultList, FilterService filterService) {
        this.wait = wait;
        this.config = config;
        this.resultList = resultList;
        this.filterService = filterService;
        this.submittedJobs = new HashSet<>();
        this.noJobPages = 0;
        //初始化时候读取已经投递的职位链接
        loadSubmittedJobs();
    }



    /**
     * 主方法：协调多城市、多关键词搜索，收集职位链接并处理
     */
    public void postJobByCity(String s) throws InterruptedException {
        JavascriptExecutor jse = (JavascriptExecutor) CHROME_DRIVER;

        // 访问Boss直聘首页
        log.info("访问Boss直聘首页...");
        CHROME_DRIVER.get("https://www.zhipin.com");
        SeleniumUtil.sleep(3);
        wait.until(driver -> jse.executeScript("return document.readyState").equals("complete"));
        log.info("首页加载完成");

        // 模拟用户行为：滚动页面
        jse.executeScript("window.scrollTo(0, 300);");
        SeleniumUtil.sleep(2);
        jse.executeScript("window.scrollTo(0, 0);");
        log.info("模拟用户滚动页面");

        // 清空 job_links.txt 文件（确保每次运行时重新创建）
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

                // 收集职位链接
                List<String> jobLinks = collectJobLinks(cityCode, keyword, originalWindow);
                if (jobLinks.isEmpty()) {
                    log.warn("城市【{}】关键词【{}】未收集到任何职位链接，跳过", cityCode, keyword);
                    continue;
                }

                // 追加写入文件
                writeJobLinksToFile(jobLinks);
            }
        }

        log.info("所有城市和关键词的职位链接收集完成");

        // 处理收集到的职位链接
        processJobLinks(originalWindow);
    }

    /**
     * 收集职位链接：构造搜索 URL，加载页面，滚动加载更多职位，提取链接
     */
    private List<String> collectJobLinks(String cityCode, String keyword, String originalWindow) {
        JavascriptExecutor jse = (JavascriptExecutor) CHROME_DRIVER;
        List<String> jobLinks = new ArrayList<>();

        // 构造搜索 URL
        String searchUrl = BASE_URL +
                JobUtils.appendParam("city", cityCode) +
                JobUtils.appendParam("jobType", config.getJobType()) +
                JobUtils.appendParam("salary", config.getSalary()) +
                JobUtils.appendListParam("experience", config.getExperience()) +
                JobUtils.appendListParam("degree", config.getDegree()) +
                JobUtils.appendListParam("scale", config.getScale()) +
                JobUtils.appendListParam("industry", config.getIndustry()) +
                JobUtils.appendListParam("stage", config.getStage()) +
                "&query=" + keyword;
        log.info("构造的搜索 URL: {}", searchUrl);

        // 导航到搜索结果页面
        try {
            CHROME_DRIVER.get(searchUrl);
            log.info("已导航到搜索结果页面");
        } catch (Exception e) {
            log.error("导航到搜索结果页面失败：{}", e.getMessage());
            return jobLinks;
        }

        // 等待页面加载
        try {
            log.info("等待搜索结果页面加载...");
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(@class, 'job-list')]")));
            log.info("搜索结果页面加载完成");
        } catch (Exception e) {
            log.error("搜索结果页面加载失败：{}", e.getMessage());
            return jobLinks;
        }

        // 检查安全验证
        String currentUrl = CHROME_DRIVER.getCurrentUrl();
        if (currentUrl.contains("security-check.html")) {
            log.warn("搜索后触发安全验证，URL：{}，暂停以便观察...", currentUrl);
            SeleniumUtil.sleep(30);
            currentUrl = CHROME_DRIVER.getCurrentUrl();
            if (currentUrl.contains("security-check.html")) {
                log.warn("安全验证后未跳回搜索页面，当前URL: {}，程序暂停...", currentUrl);
                SeleniumUtil.sleep(60);
                return jobLinks;
            }
            log.info("安全验证后跳回搜索页面，继续收集...");
        }

        // 移除 page 参数
        String modifiedUrl = currentUrl;
        try {
            if (modifiedUrl.contains("page=")) {
                modifiedUrl = modifiedUrl.replaceAll("page=\\d+(&|$)", "");
                modifiedUrl = modifiedUrl.endsWith("&") ? modifiedUrl.substring(0, modifiedUrl.length() - 1) : modifiedUrl;
                log.info("已移除 page 参数，调整后的 URL: {}", modifiedUrl);
            } else {
                log.info("URL 中不含 page 参数，无需调整: {}", modifiedUrl);
            }
        } catch (Exception e) {
            log.error("处理 URL 时失败：{}", e.getMessage());
            return jobLinks;
        }

        // 打开新标签页
        String newWindow = null;
        try {
            jse.executeScript("window.open('" + modifiedUrl + "', '_blank');");
            log.info("已在新标签页打开 URL: {}", modifiedUrl);
            SeleniumUtil.sleep(3);
            for (String windowHandle : CHROME_DRIVER.getWindowHandles()) {
                if (!windowHandle.equals(originalWindow)) {
                    newWindow = windowHandle;
                    CHROME_DRIVER.switchTo().window(newWindow);
                    log.info("已切换到新标签页，窗口句柄: {}", newWindow);
                    break;
                }
            }
        } catch (Exception e) {
            log.error("无法打开或切换到新标签页：{}", e.getMessage());
            return jobLinks;
        }

        if (newWindow == null) {
            log.error("无法切换到新标签页，跳过当前关键词");
            return jobLinks;
        }

        // 等待新标签页加载完成
        try {
            wait.until(driver -> jse.executeScript("return document.readyState").equals("complete"));
            log.info("新标签页加载完成，URL: {}", CHROME_DRIVER.getCurrentUrl());
        } catch (Exception e) {
            log.error("新标签页加载失败：{}", e.getMessage());
            CHROME_DRIVER.close();
            CHROME_DRIVER.switchTo().window(originalWindow);
            return jobLinks;
        }

        // 加载更多职位
        log.info("在新标签页中尝试加载更多职位...");
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
                SeleniumUtil.sleep(5);

                String tempUrl = CHROME_DRIVER.getCurrentUrl();
                if (!tempUrl.equals(modifiedUrl)) {
                    log.warn("加载更多职位过程中 URL 变化，当前 URL: {}，尝试恢复...", tempUrl);
                    CHROME_DRIVER.get(modifiedUrl);
                    SeleniumUtil.sleep(5);
                    continue;
                }

                previousJobCount = currentJobCount;
                loadAttempt++;
            } catch (Exception e) {
                log.warn("加载更多职位失败：{}", e.getMessage());
                loadAttempt++;
                SeleniumUtil.sleep(5);
            }
        }

        // 滚动到 footer
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
                SeleniumUtil.sleep(3);

                WebElement footer = CHROME_DRIVER.findElement(By.cssSelector("#footer"));
                if (footer != null && footer.isDisplayed()) {
                    log.info("footer 已加载到 DOM 并可见");
                    footerLoaded = true;
                    break;
                }

                currentPosition += step;
                scrollAttempt++;
            } catch (Exception e) {
                log.info("footer 尚未加载，继续滚动... 当前尝试次数: {}/{}", scrollAttempt + 1, maxScrollAttempts);
                currentPosition += step;
                scrollAttempt++;
                SeleniumUtil.sleep(3);
            }
        }

        if (!footerLoaded) {
            log.error("尝试 {} 次后仍未加载 footer，跳过当前关键词");
            CHROME_DRIVER.close();
            CHROME_DRIVER.switchTo().window(originalWindow);
            return jobLinks;
        }

        // 定位 footer 并滚动到可见
        WebElement footer;
        try {
            footer = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#footer")));
            log.info("成功定位 footer 元素，且 footer 可见");
        } catch (Exception e) {
            log.error("无法定位或 footer 不可见：{}", e.getMessage());
            CHROME_DRIVER.close();
            CHROME_DRIVER.switchTo().window(originalWindow);
            return jobLinks;
        }

        // 拦截页面跳转和模拟用户行为
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

        Long footerPosition;
        try {
            Object position = jse.executeScript("return arguments[0].getBoundingClientRect().top + window.scrollY;", footer);
            footerPosition = position instanceof Double ? ((Double) position).longValue() : (Long) position;
            log.info("footer 绝对位置: {}", footerPosition);
        } catch (Exception e) {
            log.error("无法获取 footer 绝对位置：{}", e.getMessage());
            CHROME_DRIVER.close();
            CHROME_DRIVER.switchTo().window(originalWindow);
            return jobLinks;
        }

        Long pageHeight;
        try {
            Object height = jse.executeScript("return document.body.scrollHeight;");
            pageHeight = height instanceof Double ? ((Double) height).longValue() : (Long) height;
            log.info("页面总高度: {}", pageHeight);
        } catch (Exception e) {
            log.error("无法获取页面总高度：{}", e.getMessage());
            CHROME_DRIVER.close();
            CHROME_DRIVER.switchTo().window(originalWindow);
            return jobLinks;
        }

        int maxRetries = 5;
        int retryCount = 0;
        boolean isFooterInViewport = false;
        long safeStopPosition = Math.max(0, pageHeight - 1000);
        currentPosition = (long) ((Number) jse.executeScript("return window.scrollY;")).doubleValue();

        while (currentPosition < safeStopPosition && currentPosition < footerPosition) {
            try {
                jse.executeScript("window.scrollTo({ top: arguments[0], behavior: 'smooth' });", currentPosition);
                log.info("逐步滚动到位置: {}", currentPosition);
                SeleniumUtil.sleep(3);

                String tempUrl = CHROME_DRIVER.getCurrentUrl();
                if (!tempUrl.equals(modifiedUrl)) {
                    log.warn("逐步滚动过程中 URL 变化，当前 URL: {}，尝试恢复...", tempUrl);
                    CHROME_DRIVER.get(modifiedUrl);
                    SeleniumUtil.sleep(5);
                    footer = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#footer")));
                    Object position = jse.executeScript("return arguments[0].getBoundingClientRect().top + window.scrollY;", footer);
                    footerPosition = position instanceof Double ? ((Double) position).longValue() : (Long) position;
                    log.info("重新定位 footer 后，footer 绝对位置: {}", footerPosition);
                    continue;
                }

                currentPosition += step;
            } catch (Exception e) {
                log.warn("逐步滚动失败：{}", e.getMessage());
                SeleniumUtil.sleep(3);
                continue;
            }
        }

        while (retryCount < maxRetries && !isFooterInViewport) {
            try {
                jse.executeScript("arguments[0].scrollIntoView({ behavior: 'smooth', block: 'start' });", footer);
                log.info("尝试平滑滚动到页面底部 footer，第 {} 次", retryCount + 1);
                SeleniumUtil.sleep(5);

                String script = "var rect = arguments[0].getBoundingClientRect(); var windowHeight = window.innerHeight; return rect.top >= 0 && rect.top <= windowHeight;";
                isFooterInViewport = (Boolean) jse.executeScript(script, footer);
                log.info("footer 是否在视口中: {}", isFooterInViewport);

                String tempUrl = CHROME_DRIVER.getCurrentUrl();
                if (!tempUrl.equals(modifiedUrl)) {
                    log.warn("滚动到 footer 过程中 URL 变化，当前 URL: {}，尝试恢复...", tempUrl);
                    CHROME_DRIVER.get(modifiedUrl);
                    SeleniumUtil.sleep(5);
                    footer = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#footer")));
                    Object position = jse.executeScript("return arguments[0].getBoundingClientRect().top + window.scrollY;", footer);
                    footerPosition = position instanceof Double ? ((Double) position).longValue() : (Long) position;
                    log.info("重新定位 footer 后，footer 绝对位置: {}", footerPosition);
                    retryCount++;
                    continue;
                }

                if (!isFooterInViewport) {
                    Object scrollYObj = jse.executeScript("return window.scrollY;");
                    Long scrollY = scrollYObj instanceof Double ? ((Double) scrollYObj).longValue() : (Long) scrollYObj;
                    log.info("当前滚动位置 (scrollY): {}", scrollY);
                    if (scrollY == 0) {
                        log.warn("检测到页面可能已刷新，滚动位置回到顶部，重新滚动...");
                    }
                    retryCount++;
                    continue;
                }

                log.info("已成功滚动到页面底部 footer");
            } catch (Exception e) {
                log.error("滚动到 footer 失败：{}", e.getMessage());
                retryCount++;
                SeleniumUtil.sleep(5);
            }
        }

        if (!isFooterInViewport) {
            log.error("重试 {} 次后仍无法滚动到 footer，关闭新标签页...");
            CHROME_DRIVER.close();
            CHROME_DRIVER.switchTo().window(originalWindow);
            return jobLinks;
        }

        // 检查 URL 是否变化
        currentUrl = CHROME_DRIVER.getCurrentUrl();
        if (currentUrl.contains("security-check.html")) {
            log.warn("滚动到 footer 后触发安全验证，URL：{}，暂停以便观察...", currentUrl);
            SeleniumUtil.sleep(30);
            CHROME_DRIVER.close();
            CHROME_DRIVER.switchTo().window(originalWindow);
            return jobLinks;
        }

        // 提取职位卡片链接
        List<WebElement> jobCards = new ArrayList<>();
        try {
            WebElement jobListContainer = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"wrap\"]/div[2]/div[3]/div/div/div[1]/ul")));
            log.info("成功定位职位列表容器");

            int cardIndex = 1;
            while (true) {
                String jobCardXpath = "//*[@id=\"wrap\"]/div[2]/div[3]/div/div/div[1]/ul/div[" + cardIndex + "]";
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

        // 关闭新标签页
        try {
            CHROME_DRIVER.navigate().refresh();
            log.info("在新标签页中刷新页面...");
            wait.until(driver -> jse.executeScript("return document.readyState").equals("complete"));
            log.info("页面刷新完成");
        } catch (Exception e) {
            log.warn("刷新页面失败：{}", e.getMessage());
        }

        CHROME_DRIVER.close();
        CHROME_DRIVER.switchTo().window(originalWindow);
        log.info("已关闭新标签页，切换回原标签页，窗口句柄: {}", originalWindow);

        return jobLinks;
    }

    /**
     * 将职位链接追加写入文件
     */
    private void writeJobLinksToFile(List<String> jobLinks) {
        try (FileWriter writer = new FileWriter("job_links.txt", true)) { // 追加模式
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
     * 处理职位链接：读取 job_links.txt，打开新标签页，提取信息
     */
    private void processJobLinks(String originalWindow) throws InterruptedException {
        JavascriptExecutor jse = (JavascriptExecutor) CHROME_DRIVER;
        // 读取 job_links.txt 文件
        List<String> jobLinks = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(Paths.get("job_links.txt"));
            for (String line : lines) {
                if (line.contains(":")) {
                    String[] parts = line.split(": ");
                    if (parts.length > 1 && parts[1].startsWith("http")) {
                        String jobHref = parts[1].trim();
                        // 排除已投递的职位
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
            return;
        }

        if (jobLinks.isEmpty()) {
            log.warn("job_links.txt 中没有有效的职位链接，跳过处理");
            return;
        }

        // 遍历每个职位链接
        for (int i = 0; i < jobLinks.size(); i++) {
            // 检查成功投递数量是否达到上限
            if (successfulSubmissions >= MAX_SUCCESSFUL_SUBMISSIONS) {
                log.info("已成功投递 {} 个职位，达到上限，终止处理", successfulSubmissions);
                break;
            }

            // 检查连续错误次数是否达到上限
            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                log.warn("连续投递失败 {} 次，达到上限，终止处理", consecutiveErrors);
                break;
            }
            String jobHref = jobLinks.get(i);
            log.info("处理职位链接 {}: {}", i + 1, jobHref);

            // 在新标签页中打开职位详情页
            String newWindow = null;
            try {
                jse.executeScript("window.open('" + jobHref + "', '_blank');");
                SeleniumUtil.sleep(3);
                for (String windowHandle : CHROME_DRIVER.getWindowHandles()) {
                    if (!windowHandle.equals(originalWindow)) {
                        newWindow = windowHandle;
                        CHROME_DRIVER.switchTo().window(newWindow);
                        log.info("已切换到新标签页，窗口句柄: {}", newWindow);
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("无法打开新标签页：{}", e.getMessage());
                continue;
            }

            if (newWindow == null) {
                log.error("无法切换到新标签页，跳过当前职位链接");
                continue;
            }

            // 等待页面加载完成
            try {
                wait.until(driver -> jse.executeScript("return document.readyState").equals("complete"));
                log.info("职位详情页加载完成，URL: {}", CHROME_DRIVER.getCurrentUrl());
            } catch (Exception e) {
                log.error("职位详情页加载失败：{}", e.getMessage());
                CHROME_DRIVER.close();
                CHROME_DRIVER.switchTo().window(originalWindow);
                continue;
            }

            // 检查是否触发安全验证
            String currentUrl = CHROME_DRIVER.getCurrentUrl();
            if (currentUrl.contains("security-check.html")) {
                log.warn("访问职位详情页后触发安全验证，URL：{}，暂停以便观察...", currentUrl);
                SeleniumUtil.sleep(30);
                CHROME_DRIVER.close();
                CHROME_DRIVER.switchTo().window(originalWindow);
                continue;
            }

            // 检查 HR 是否不活跃
            if (isDeadHR()) {
                log.info("HR 不活跃，根据配置跳过此职位：{}", jobHref);
                CHROME_DRIVER.close();
                CHROME_DRIVER.switchTo().window(originalWindow);
                continue; // 跳过不活跃 HR 的职位
            }

            // 提取页面元素信息（复用 Boss.java 的选择器）
            Job job = new Job();
            job.setHref(jobHref);

            try {
                // 提取职位名称
                try {
                    WebElement jobNameElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"main\"]/div[1]/div/div/div[1]/div[2]/h1")));
                    String jobName = jobNameElement.getText();
                    job.setJobName(jobName);
                    log.info("提取职位名称: {}", jobName);
                } catch (Exception e) {
                    log.warn("无法提取职位名称：{}", e.getMessage());
                    job.setJobName("未知职位");
                }

                // 提取薪资
                try {
                    WebElement salaryElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"main\"]/div[1]/div/div/div[1]/div[2]/span")));
                    String salary = salaryElement.getText();
                    job.setSalary(salary);
                    log.info("提取薪资: {}", salary);
                } catch (Exception e) {
                    log.warn("无法提取薪资：{}", e.getMessage());
                    job.setSalary("未知薪资");
                }

                // 提取公司名称
                try {
                    WebElement companyElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"main\"]/div[3]/div/div[1]/div[2]/div/a[2]")));
                    String companyName = companyElement.getText();
                    job.setCompanyName(companyName);
                    log.info("提取公司名称: {}", companyName);
                } catch (Exception e) {
                    log.warn("无法提取公司名称：{}", e.getMessage());
                    job.setCompanyName("未知公司");
                }

                // 提取招聘者信息
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

                // 提取职位区域
                try {
                    WebElement cityElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"main\"]/div[1]/div/div/div[1]/p/a")));
                    String jobArea = cityElement.getText();
                    job.setJobArea(jobArea);
                    log.info("提取职位区域: {}", jobArea);
                } catch (Exception e) {
                    log.warn("无法提取职位区域：{}", e.getMessage());
                    job.setJobArea("未知区域");
                }

                // 提取职位标签
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
                // 投递逻辑
                try {
                    // 等待“立即沟通”按钮
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#main > div.job-banner > div > div > div.info-primary > div.job-op > div.btn-container > div > a")));
                    WebElement btn = CHROME_DRIVER.findElement(By.cssSelector("#main > div.job-banner > div > div > div.info-primary > div.job-op > div.btn-container > div > a"));
                    if ("立即沟通".equals(btn.getText())) {
                        SeleniumUtil.sleep(10);
                        String waitTime = config.getWaitTime();
                        int sleepTime = 10;
                        if (waitTime != null) {
                            try {
                                sleepTime = Integer.parseInt(waitTime);
                            } catch (NumberFormatException e) {
                                log.error("等待时间转换异常！！");
                            }
                        }
                        SeleniumUtil.sleep(sleepTime);

                        AiFilter filterResult = null;
                        if (config.getEnableAI()) {
                            String jd = CHROME_DRIVER.findElement(By.xpath("//div[@class='job-sec-text']")).getText();
                            filterResult = checkJob("", job.getJobName(), jd); // 假设关键词为空
                        }

                        // 点击“立即沟通”按钮
                        btn.click();
                        SeleniumUtil.sleep(2); // 等待弹窗加载
                        // 检查是否弹出身份验证窗口
                        try {
//                            WebElement dialog = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(text(), '身份验证问题')]")));
//                            log.info("检测到身份验证窗口，尝试关闭...");

                            // 定位关闭按钮并点击
                            WebElement closeIcon = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("/html/body/div[12]/div[2]/div[1]/a/i")));
                            closeIcon.click();
                            log.info("身份验证窗口已关闭");

                            // 等待弹窗消失
//                            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//div[contains(text(), '身份验证问题')]")));

                            // 再次点击“立即沟通”按钮
//                            btn = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("")));
                            btn.click();
                            log.info("再次点击‘立即沟通’按钮");
                            SeleniumUtil.sleep(2);
                        } catch (Exception e) {
                            log.debug("未检测到身份验证窗口，继续投递流程");
                        }

                        if (isLimit()) {
                            log.warn("今日沟通次数已达上限，停止投递");
                            CHROME_DRIVER.close();
                            CHROME_DRIVER.switchTo().window(originalWindow);
                            break; // 停止整个循环
                        }

                        // 检查是否已发送消息
                        if (hasSentMessage()) {
                            log.info("已发送消息，跳过投递：{}", jobHref);
                            CHROME_DRIVER.close();
                            CHROME_DRIVER.switchTo().window(originalWindow);
                            continue;
                        }

                        //未发送消息，继续投递
                        try {
                            // 等待输入框可见并激活
                            WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#chat-input")));
                            // 如果输入框不可见，尝试通过 JavaScript 激活
                            if (!input.isDisplayed()) {
                                log.warn("聊天输入框不可见，尝试通过 JavaScript 激活");
                                jse.executeScript("arguments[0].style.display='block'; arguments[0].focus();", input);
                            }
                            input.click();

                            // 发送消息
                            String message = (filterResult != null && filterResult.getResult() && isValidString(filterResult.getMessage()))
                                    ? filterResult.getMessage()
                                    : config.getSayHi();
                            input.sendKeys(message);
                            WebElement send = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("#container > div > div > div.chat-conversation > div.message-controls > div > div:nth-child(2) > div.chat-op > button")));
                            send.click();
                            SeleniumUtil.sleep(3);

                            // 验证消息是否发送成功
                            if (hasSentMessage()) {
                                log.info("消息发送成功：{}", message);
                                String company = job.getCompanyName() != null ? job.getCompanyName() : "未知公司: " + job.getHref();
                                Boolean imgResume = sendResume(company);
                                SeleniumUtil.sleep(2);
                                String position = job.getJobName() + " " + job.getSalary() + " " + job.getJobArea();
                                log.info("投递成功【{}】公司，【{}】职位，招聘官:【{}】{}",
                                        company, position, recruiter, imgResume ? "发送图片简历成功！" : "");
                                submittedJobs.add(jobHref);
                                saveSubmittedJob(jobHref); // 持久化投递记录
                                resultList.add(job);
                                successfulSubmissions++;//成功投递计数器加1.
                                consecutiveErrors = 0; // 成功投递，重置错误计数器
                            } else {
                                log.error("消息发送失败，未在聊天记录中找到自己的消息：{}", message);
                                consecutiveErrors++;
                            }
                        } catch (Exception e) {
                            log.error("发送消息失败: {}", e.getMessage());
                            consecutiveErrors++;
                        }
                    } else {
                        log.info("无法投递，按钮状态异常：{}", btn.getText());
                        consecutiveErrors++;
                    }
                } catch (Exception e) {
                    log.error("投递失败：{}", e.getMessage());
                    consecutiveErrors++;
                }

            } catch (Exception e) {
                log.error("提取职位信息失败：{}", e.getMessage());
                consecutiveErrors++;
            }

                // 关闭新标签页
                try {
                    CHROME_DRIVER.close();
                    CHROME_DRIVER.switchTo().window(originalWindow);
                    log.info("已关闭新标签页，切换回原标签页，窗口句柄: {}", originalWindow);
                } catch (Exception e) {
                    log.error("关闭新标签页失败：{}", e.getMessage());
                    for (String windowHandle : CHROME_DRIVER.getWindowHandles()) {
                        if (!windowHandle.equals(originalWindow)) {
                            CHROME_DRIVER.switchTo().window(windowHandle);
                            CHROME_DRIVER.close();
                        }
                    }
                    CHROME_DRIVER.switchTo().window(originalWindow);
                }

                // 模拟用户行为：随机延迟
                SeleniumUtil.sleep((int) (Math.random() * 3) + 2);
            }

        log.info("所有职位链接处理完成，共处理 {} 个职位", resultList.size());
    }


    // 方法：处理职位
    private void processJobs(String keyword) {
        JavascriptExecutor jse = (JavascriptExecutor) CHROME_DRIVER;

        // 检查页面加载状态
        wait.until(driver -> jse.executeScript("return document.readyState").equals("complete"));
        log.info("页面加载成功");

        String currentUrl = CHROME_DRIVER.getCurrentUrl();
        if (currentUrl.contains("security-check.html")) {
            log.warn("检测到安全检查页面: {}，暂停等待人工操作...", currentUrl);
            SeleniumUtil.sleep(30); // 允许人工处理安全检查
            // 检查URL是否变化，确保安全检查已通过
            currentUrl = CHROME_DRIVER.getCurrentUrl();
            if (currentUrl.contains("security-check.html")) {
                log.warn("仍然处于安全检查页面，等待进一步...");
                SeleniumUtil.sleep(60); // 再等待60秒
                return;
            }
            log.info("安全检查通过，继续执行...");
        }

        WebElement jobListContainer;
        try {
            // 等待职位列表容器加载
            jobListContainer = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(Elements.JOB_LIST_XPATH)));
            Thread.sleep(1000);
        } catch (Exception e) {
            log.error("无法定位职位列表容器：{}", e.getMessage());
            return;
        }

        // 获取所有职位卡片
        List<WebElement> jobCards;
        try {
            jobCards = CHROME_DRIVER.findElements(By.cssSelector("div.job-card-wrapper"));
            log.info("当前页面共找到 {} 个职位卡片", jobCards.size());
        } catch (Exception e) {
            log.error("无法获取职位卡片：{}", e.getMessage());
            return;
        }

        // 如果没有职位卡片，结束
        if (jobCards.isEmpty()) {
            log.info("当前页面没有职位，结束操作。");
            return;
        }

        // 打印所有职位卡片的 HTML 内容
        log.info("开始打印所有职位卡片的 HTML 内容：");
        for (int i = 0; i < jobCards.size(); i++) {
            WebElement jobCard = jobCards.get(i);
            try {
                String jobCardHtml = jobCard.getAttribute("outerHTML");
                log.info("职位卡片 {} HTML 内容：\n{}", i + 1, jobCardHtml);
            } catch (Exception e) {
                log.warn("无法获取职位卡片 {} 的 HTML 内容：{}", i + 1, e.getMessage());
            }
        }

        log.info("职位卡片 HTML 内容打印完成，总共打印 {} 个职位卡片", jobCards.size());
    }


    // 处理职位投递逻辑
    private void processJob(WebElement jobCard, String jobHref) {
        // 实现职位投递的相关操作
        log.info("开始处理职位投递逻辑: {}", jobHref);
        // 这里执行职位详情页面处理，发送消息等
    }


    // 以下是原有的投递相关方法，暂时保留但不会被调用
    @SneakyThrows
    private Integer resumeSubmission(WebElement jobCard, String jobHref, String keyword) {
        JavascriptExecutor jse = (JavascriptExecutor) CHROME_DRIVER;
        Long scrollPosition = (Long) jse.executeScript("return window.scrollY;");

        String jobName = "未知职位";
        try {
            jobName = jobCard.findElement(By.cssSelector(Elements.JOB_NAME_CSS)).getText();
            log.info("提取职位名称: {}", jobName);
        } catch (Exception e) {
            log.warn("未找到职位名称：{}", e.getMessage());
        }

        String salary = "未知薪资";
        try {
            WebElement salaryElement = jobCard.findElement(By.cssSelector(Elements.JOB_SALARY_CSS));
            salary = salaryElement.getText();
            log.info("提取薪资信息: {}", salary);
        } catch (Exception e) {
            log.warn("未找到薪资信息：{}", e.getMessage());
        }

        String companyName = "未知公司";
        try {
            companyName = jobCard.findElement(By.cssSelector(Elements.COMPANY_NAME_CSS)).getText();
            log.info("提取公司名称: {}", companyName);
        } catch (Exception e) {
            log.warn("未找到公司名称：{}", e.getMessage());
        }

        String jobArea = "未知区域";
        try {
            jobArea = jobCard.findElement(By.cssSelector(Elements.JOB_AREA_CSS)).getText();
            log.info("提取职位区域: {}", jobArea);
        } catch (Exception e) {
            log.warn("未找到职位区域：{}", e.getMessage());
        }

        StringBuilder tags = new StringBuilder();
        for (WebElement tagElement : jobCard.findElements(By.cssSelector(Elements.JOB_TAGS_CSS))) {
            String tag = tagElement.getText();
            tags.append(tag).append("·");
            log.debug("提取职位标签: {}", tag);
        }
        String jobTags = tags.length() > 0 ? tags.substring(0, tags.length() - 1) : "";
        log.info("职位标签: {}", jobTags);

        log.info("收集到职位信息 - 职位: {}, 公司: {}, 薪资: {}, 地点: {}, 标签: {}, 链接: {}",
                jobName, companyName, salary, jobArea, jobTags, jobHref);

        if (filterService.filterJob(companyName, "", jobName) || !filterService.isTargetJob(keyword, jobName)) {
            log.info("职位被过滤：公司【{}】，职位【{}】", companyName, jobName);
            return 0;
        }
        if (filterService.isSalaryNotExpected(salary)) {
            log.info("已过滤:【{}】公司【{}】岗位薪资【{}】不符合投递要求", companyName, jobName, salary);
            return 0;
        }

        log.info("点击职位卡片，加载右侧详情...");
        jobCard.findElement(By.cssSelector(Elements.JOB_HREF_CSS)).click();
        WebElement detailContainer;
        try {
            detailContainer = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(Elements.JOB_DETAIL_CONTAINER_CSS)));
            jse.executeScript("arguments[0].scrollTo(0, arguments[0].scrollHeight)", detailContainer);
            log.info("成功加载右侧详情区域");
        } catch (Exception e) {
            log.warn("无法加载或滚动右侧详情区域：{}", e.getMessage());
        }

        String recruiterName = "未知招聘者";
        String recruiterStatus = "未知状态";
        try {
            WebElement recruiterNameElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(Elements.RECRUITER_NAME_IN_DETAIL_XPATH)));
            recruiterName = recruiterNameElement.getText().trim();
            log.info("提取招聘者姓名: {}", recruiterName);
        } catch (Exception e) {
            log.warn("未找到招聘者姓名：{}", e.getMessage());
        }
        try {
            WebElement recruiterStatusElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(Elements.RECRUITER_STATUS_XPATH)));
            recruiterStatus = recruiterStatusElement.getText();
            log.info("提取招聘者状态: {}", recruiterStatus);
        } catch (Exception e) {
            log.warn("未找到招聘者状态：{}", e.getMessage());
        }
        String recruiterText = recruiterName + " " + recruiterStatus;
        log.info("招聘者信息: {}", recruiterText);

        Job job = new Job();
        job.setRecruiter(recruiterText);
        job.setHref(jobHref);
        job.setJobName(jobName);
        job.setJobArea(jobArea);
        job.setSalary(salary);
        job.setCompanyTag(jobTags);
        job.setCompanyName(companyName);

        String originalWindow = CHROME_DRIVER.getWindowHandle();
        log.info("打开新窗口访问职位详情页: {}", jobHref);
        jse.executeScript("window.open(arguments[0], '_blank')", jobHref);
        ArrayList<String> tabs = new ArrayList<>(CHROME_DRIVER.getWindowHandles());
        CHROME_DRIVER.switchTo().window(tabs.getLast());

        try {
            log.info("等待立即沟通按钮加载...");
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(Elements.CHAT_BUTTON_CSS)));
            WebElement btn = CHROME_DRIVER.findElement(By.cssSelector(Elements.CHAT_BUTTON_CSS));
            String buttonText = btn.getText();
            log.info("立即沟通按钮文本: {}", buttonText);

            if (!"立即沟通".equals(buttonText)) {
                log.info("职位【{}】无法沟通，可能是已投递或不符合条件", jobName);
                return 0;
            }

            if (filterService.isDeadHR()) {
                log.info("该HR已被过滤，跳过投递");
                closeWindow(tabs);
                CHROME_DRIVER.switchTo().window(originalWindow);
                jse.executeScript("window.scrollTo(0, arguments[0]);", scrollPosition);
                SeleniumUtil.sleep(1);
                return 0;
            }

            log.info("模拟用户等待行为...");
            simulateWait();
            SeleniumUtil.sleep(10);
            int sleepTime = parseWaitTime();
            log.info("额外等待时间: {} 秒", sleepTime);
            SeleniumUtil.sleep(sleepTime);

            AiFilter filterResult = null;
            if (config.getEnableAI()) {
                log.info("启用AI过滤，提取职位描述...");
                String jd = CHROME_DRIVER.findElement(By.xpath(Elements.JOB_DESCRIPTION_XPATH)).getText();
                log.debug("职位描述: {}", jd);
                filterResult = filterService.checkJob(keyword, job.getJobName(), jd);
                log.info("AI过滤结果: {}", filterResult != null ? filterResult.toString() : "无结果");
            }

            log.info("点击立即沟通按钮...");
            btn.click();
            if (isLimit()) {
                log.info("检测到投递上限，结束投递流程");
                closeWindow(tabs);
                CHROME_DRIVER.switchTo().window(originalWindow);
                jse.executeScript("window.scrollTo(0, arguments[0]);", scrollPosition);
                return -1;
            }

            try {
                log.info("等待聊天输入框加载...");
                WebElement input = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(Elements.CHAT_INPUT_XPATH)));
                input.click();
                log.info("点击聊天输入框");

                WebElement dialog = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(Elements.DIALOG_CONTAINER_XPATH)));
                String dialogText = dialog.getText();
                log.info("对话框内容: {}", dialogText);

                if ("不匹配".equals(dialogText)) {
                    log.info("职位不匹配，跳过投递");
                    closeWindow(tabs);
                    CHROME_DRIVER.switchTo().window(originalWindow);
                    jse.executeScript("window.scrollTo(0, arguments[0]);", scrollPosition);
                    return 0;
                }

                String message = (filterResult != null && filterResult.getResult() && isValidString(filterResult.getMessage()))
                        ? filterResult.getMessage()
                        : config.getSayHi();
                log.info("发送消息: {}", message);
                input.sendKeys(message);

                log.info("等待发送按钮可点击...");
                WebElement send = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(Elements.SEND_BUTTON_XPATH)));
                send.click();
                log.info("点击发送按钮");
                SeleniumUtil.sleep(3);

                WebElement recruiterNameElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(Elements.RECRUITER_NAME_XPATH)));
                WebElement recruiterTitleElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(Elements.RECRUITER_TITLE_XPATH)));
                String recruiter = recruiterNameElement.getText() + " " + recruiterTitleElement.getText();
                log.info("聊天页面招聘者信息: {}", recruiter);

                WebElement companyElement = null;
                try {
                    companyElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(Elements.COMPANY_XPATH)));
                    log.info("提取公司信息: {}", companyElement.getText());
                } catch (Exception e) {
                    log.info("获取公司名异常：{}", e.getMessage());
                }

                String company = (companyElement != null) ? companyElement.getText() : "未知公司: " + job.getHref();
                job.setCompanyName(company);
                log.info("更新公司名称: {}", company);

                WebElement positionNameElement = null;
                try {
                    positionNameElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(Elements.POSITION_NAME_XPATH)));
                    log.info("提取详情页职位名称: {}", positionNameElement.getText());
                } catch (Exception e) {
                    log.warn("未找到职位名称：{}", e.getMessage());
                }

                WebElement salaryElement = null;
                try {
                    salaryElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(Elements.POSITION_SALARY_XPATH)));
                    log.info("提取详情页薪资: {}", salaryElement.getText());
                } catch (Exception e) {
                    log.warn("未找到薪资信息：{}", e.getMessage());
                }

                WebElement cityElement = null;
                try {
                    cityElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(Elements.POSITION_CITY_XPATH)));
                    log.info("提取详情页城市: {}", cityElement.getText());
                } catch (Exception e) {
                    log.warn("未找到城市信息：{}", e.getMessage());
                }

                String position = (positionNameElement != null ? positionNameElement.getText() : "未知职位") + " " +
                        (salaryElement != null ? salaryElement.getText() : "未知薪资") + " " +
                        (cityElement != null ? cityElement.getText() : "未知城市");
                log.info("职位详情: {}", position);

                Boolean imgResume = sendResume(company);
                SeleniumUtil.sleep(2);
                log.info("投递完成 - 公司: {}, 职位: {}, 招聘官: {}, 图片简历: {}",
                        company, position, recruiter, imgResume ? "发送成功" : "未发送");
                resultList.add(job);
                submittedJobs.add(job.getHref());
                noJobPages = 0;
            } catch (Exception e) {
                log.error("发送消息失败: {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            Optional<WebElement> element = SeleniumUtil.findElement(Elements.ERROR_CONTENT_XPATH, "");
            if (element.isPresent() && element.get().getText().contains("异常访问")) {
                log.info("检测到异常访问，需手动验证");
                closeWindow(tabs);
                CHROME_DRIVER.switchTo().window(originalWindow);
                jse.executeScript("window.scrollTo(0, arguments[0]);", scrollPosition);
                return -2;
            }
        } finally {
            log.info("关闭详情页窗口，返回主窗口");
            closeWindow(tabs);
            CHROME_DRIVER.switchTo().window(originalWindow);
            jse.executeScript("window.scrollTo(0, arguments[0]);", scrollPosition);
        }

        return 0;
    }

    private int parseWaitTime() {
        String waitTime = config.getWaitTime();
        int sleepTime = 10;
        if (waitTime != null) {
            try {
                sleepTime = Integer.parseInt(waitTime);
                log.debug("解析等待时间: {} 秒", sleepTime);
            } catch (NumberFormatException e) {
                log.error("等待时间转换异常！！", e);
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

    private void closeWindow(ArrayList<String> tabs) {
        SeleniumUtil.sleep(1);
        CHROME_DRIVER.close();
        CHROME_DRIVER.switchTo().window(tabs.get(0));
        log.debug("窗口切换完成，当前窗口: {}", tabs.get(0));
    }

    private void simulateWait() {
        log.info("模拟用户等待行为...");
        for (int i = 0; i < 3; i++) {
            ACTIONS.sendKeys(" ").perform();
            SeleniumUtil.sleep(1);
            log.debug("模拟按键操作，第 {} 次", i + 1);
        }
        ACTIONS.keyDown(Keys.CONTROL)
                .sendKeys(Keys.HOME)
                .keyUp(Keys.CONTROL)
                .perform();
        SeleniumUtil.sleep(1);
        log.debug("模拟滚动到顶部");
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
            // 尝试获取 HR 的活跃时间
            String activeTimeText = CHROME_DRIVER.findElement(By.xpath("//span[@class='boss-active-time']")).getText();
            log.info("{}：{}", filterService.getCompanyAndHR(), activeTimeText);
            // 如果 HR 活跃状态符合预期，则返回 true
            return containsDeadStatus(activeTimeText, deadStatus);
        } catch (Exception e) {
            log.info("没有找到【{}】的活跃状态, 默认此岗位将会投递...", filterService.getCompanyAndHR());
            return false;
        }
    }

    public static boolean containsDeadStatus(String activeTimeText, List<String> deadStatus) {
        for (String status : deadStatus) {
            if (activeTimeText.contains(status)) {
                return true;// 一旦找到包含的值，立即返回 true
            }
        }
        return false;// 如果没有找到，返回 false
    }

    private AiFilter checkJob(String keyword, String jobName, String jd) {
        AiConfig aiConfig = AiConfig.init();
        String requestMessage = String.format(aiConfig.getPrompt(), aiConfig.getIntroduce(), keyword, jobName, jd, config.getSayHi());
        String result = AiService.sendRequest(requestMessage);
        return result.contains("false") ? new AiFilter(false) : new AiFilter(true, result);
    }

    /**
     * 从 submitted_jobs.txt 加载已投递的职位链接
     */
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

    /**
     * 将投递成功的职位链接写入 submitted_jobs.txt
     */
    private void saveSubmittedJob(String jobHref) {
        try (FileWriter writer = new FileWriter("submitted_jobs.txt", true)) {
            writer.write(jobHref + "\n");
            writer.flush();
            log.info("已将职位链接写入 submitted_jobs.txt: {}", jobHref);
        } catch (IOException e) {
            log.error("写入 submitted_jobs.txt 失败：{}", e.getMessage());
        }
    }

    /**
     * 检查聊天记录是否包含自己发送的消息
     */
    private boolean hasSentMessage() {
        try {
            // 等待聊天记录容器加载
            WebElement chatConversation = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("ul.im-list")));
            log.debug("成功定位聊天记录容器：ul.im-list");

            // 查找自己发送的消息
            List<WebElement> selfMessages = chatConversation.findElements(By.cssSelector("li.message-item.item-myself"));
            if (!selfMessages.isEmpty()) {
                log.info("找到自己发送的消息，消息数量：{}", selfMessages.size());
                // 可选：打印消息内容以供调试
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