package com.getjobs.worker.liepin;

import com.getjobs.worker.utils.JobUtils;
import com.getjobs.worker.utils.PlaywrightUtil;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.getjobs.worker.liepin.Locators.*;
import static com.getjobs.worker.utils.Bot.sendMessageByTime;
import static com.getjobs.worker.utils.JobUtils.formatDuration;


/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Slf4j
public class Liepin {
    static {
        // 在类加载时就设置日志文件名，确保Logger初始化时能获取到正确的属性
        System.setProperty("log.name", "liepin");
    }

    static String homeUrl = "https://www.liepin.com/";
    static String cookiePath = "./src/main/java/liepin/cookie.json";
    static int maxPage = 50;
    static List<String> resultList = new ArrayList<>();
    static String baseUrl = "https://www.liepin.com/zhaopin/?";
    static LiepinConfig config = LiepinConfig.init();
    static Date startDate;

    /**
     * 保存页面源码到日志和文件，用于调试
     */
    private static void savePageSource(Page page, String context) {
        try {
            String pageSource = page.content();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            
            // 保存完整源码到文件
            Path sourceDir = Paths.get("./target/logs/page_sources");
            Files.createDirectories(sourceDir);
            
            String fileName = String.format("liepin_page_%s_%s.html", context.replaceAll("[^a-zA-Z0-9]", "_"), timestamp);
            Path sourceFile = sourceDir.resolve(fileName);
            Files.write(sourceFile, pageSource.getBytes("UTF-8"));
            
            log.info("完整页面源码已保存到文件: {}", sourceFile.toAbsolutePath());
            
        } catch (IOException e) {
            log.error("保存页面源码失败: {}", e.getMessage());
        }
    }



    public static void main(String[] args) {
        PlaywrightUtil.init();
        startDate = new Date();
        login();
        for (String keyword : config.getKeywords()) {
            submit(keyword);
        }
        printResult();
    }

    private static void printResult() {
        String message = String.format("\n猎聘投递完成，共投递%d个岗位，用时%s", resultList.size(), formatDuration(startDate, new Date()));
        log.info(message);
        sendMessageByTime(message);
        resultList.clear();
        PlaywrightUtil.close();
    }


    @SneakyThrows
    private static void submit(String keyword) {
        Page page = PlaywrightUtil.getPageObject();
        page.navigate(getSearchUrl() + "&key=" + keyword);
        
        // 等待分页元素加载
        page.waitForSelector(PAGINATION_BOX, new Page.WaitForSelectorOptions().setTimeout(10000));
        Locator paginationBox = page.locator(PAGINATION_BOX);
        Locator lis = paginationBox.locator("li");
        setMaxPage(lis);
        
        for (int i = 0; i < maxPage; i++) {
            try {
                // 尝试关闭订阅弹窗
                Locator closeBtn = page.locator(SUBSCRIBE_CLOSE_BTN);
                if (closeBtn.count() > 0) {
                    closeBtn.click();
                }
            } catch (Exception ignored) {
            }
            
            // 等待岗位卡片加载
            page.waitForSelector(JOB_CARDS, new Page.WaitForSelectorOptions().setTimeout(10000));
            log.info("正在投递【{}】第【{}】页...", keyword, i + 1);
            submitJob();
            log.info("已投递第【{}】页所有的岗位...\n", i + 1);
            
            // 查找下一页按钮
            paginationBox = page.locator(PAGINATION_BOX);
            Locator nextPage = paginationBox.locator(NEXT_PAGE);
            if (nextPage.count() > 0 && nextPage.getAttribute("disabled") == null) {
                nextPage.click();
                // PlaywrightUtil.sleep(1); // 休息一秒
            } else {
                break;
            }
        }
        log.info("【{}】关键词投递完成！", keyword);
    }

    private static String getSearchUrl() {
        return baseUrl +
                JobUtils.appendParam("city", config.getCityCode()) +
                JobUtils.appendParam("salary", config.getSalary()) +
                JobUtils.appendParam("pubTime", config.getPubTime()) +
                "&currentPage=" + 0 + "&dq=" + config.getCityCode();
    }


    private static void setMaxPage(Locator lis) {
        try {
            int count = lis.count();
            if (count >= 2) {
                String pageText = lis.nth(count - 2).textContent();
                int page = Integer.parseInt(pageText);
                if (page > 1) {
                    maxPage = page;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void submitJob() {
        Page page = PlaywrightUtil.getPageObject();
        
        // 等待页面完全加载
        // try {
        //     page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
        // } catch (Exception e) {
        //     log.warn("等待页面网络空闲超时，继续执行: {}", e.getMessage());
        // }
        
        // 获取hr数量
        Locator jobCards = page.locator(JOB_CARDS);
        
        // 等待岗位卡片加载完成
        // try {
        //     jobCards.first().waitFor(new Locator.WaitForOptions().setTimeout(10000));
        // } catch (Exception e) {
        //     log.warn("等待岗位卡片加载超时: {}", e.getMessage());
        // }
        
        int count = jobCards.count();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {

            Locator jobTitleElements = page.locator(JOB_TITLE);
            Locator companyNameElements = page.locator(COMPANY_NAME);
            Locator salaryElements = page.locator(JOB_SALARY);
            
            if (i >= jobTitleElements.count() || i >= companyNameElements.count() || i >= salaryElements.count()) {
                continue;
            }
            
            String jobName = jobTitleElements.nth(i).textContent().replaceAll("\n", " ").replaceAll("【 ", "[").replaceAll(" 】", "]");
            String companyName = companyNameElements.nth(i).textContent().replaceAll("\n", " ");
            String salary = salaryElements.nth(i).textContent().replaceAll("\n", " ");
            String recruiterName = null;
            
            try {
                // 获取当前岗位卡片
                Locator currentJobCard = page.locator(JOB_CARDS).nth(i);
                
                // 使用JavaScript滚动到卡片位置，更稳定
                try {
                    // 先滚动到卡片位置
                    page.evaluate("(element) => element.scrollIntoView({behavior: 'instant', block: 'center'})", currentJobCard.elementHandle());
                    // PlaywrightUtil.sleep(1); // 等待滚动完成
                    
                    // 再次确保元素在视窗中
                    page.evaluate("(element) => { const rect = element.getBoundingClientRect(); if (rect.top < 0 || rect.bottom > window.innerHeight) { element.scrollIntoView({behavior: 'instant', block: 'center'}); } }", currentJobCard.elementHandle());
                    // PlaywrightUtil.sleep(1);
                } catch (Exception scrollError) {
                    log.warn("JavaScript滚动失败，尝试页面滚动: {}", scrollError.getMessage());
                    // 备用方案：滚动页面到大概位置
                    page.evaluate("window.scrollBy(0, " + (i * 200) + ")");
                    // PlaywrightUtil.sleep(1);
                }
                
                // 查找HR区域 - 尝试多种可能的HR标签选择器
                Locator hrArea = null;
                String[] hrSelectors = {
                    ".recruiter-info-box",  // 根据页面源码，这是主要的HR区域类名
                    ".recruiter-info, .hr-info, .contact-info",
                    "[class*='recruiter'], [class*='hr-'], [class*='contact']",
                    ".job-card-footer, .card-footer",
                    ".job-bottom, .bottom-info"
                };
                
                for (String selector : hrSelectors) {
                    Locator tempHrArea = currentJobCard.locator(selector);
                    if (tempHrArea.count() > 0) {
                        hrArea = tempHrArea.first();
                        log.debug("找到HR区域，使用选择器: {}", selector);
                        break;
                    }
                }
                
                // 如果找不到特定的HR区域，使用整个卡片
                if (hrArea == null) {
                    log.debug("未找到特定HR区域，使用整个岗位卡片");
                    hrArea = currentJobCard;
                }
                
                // 鼠标悬停到HR区域，触发按钮显示 - 简化悬停逻辑
                boolean hoverSuccess = false;
                int hoverRetries = 3;
                for (int retry = 0; retry < hoverRetries; retry++) {
                    try {
                        // 检查HR区域是否可见，如果不可见则跳过悬停
                        if (!hrArea.isVisible()) {
                            log.debug("HR区域不可见，跳过悬停操作");
                            hoverSuccess = true; // 设为成功，继续后续流程
                            break;
                        }
                        
                        // 直接悬停，不再进行复杂的微调
                        hrArea.hover(new Locator.HoverOptions().setTimeout(5000));
                        hoverSuccess = true;
                        break;
                    } catch (Exception hoverError) {
                        log.warn("第{}次悬停失败: {}", retry + 1, hoverError.getMessage());
                        if (retry < hoverRetries - 1) {
                            // 重试前重新滚动确保元素可见
                            try {
                                page.evaluate("(element) => element.scrollIntoView({behavior: 'instant', block: 'center'})", currentJobCard.elementHandle());
                                Thread.sleep(500); // 等待滚动完成
                            } catch (Exception e) {
                                log.warn("重试前滚动失败: {}", e.getMessage());
                            }
                        }
                    }
                }
                
                if (!hoverSuccess) {
                    log.warn("悬停操作失败，但继续查找按钮");
                    // 不再跳过，而是继续查找按钮，因为有些按钮可能不需要悬停就能显示
                }
                
                // PlaywrightUtil.sleep(1); // 等待按钮显示
                
                // 获取hr名字
                try {
                    Locator hrNameElement = currentJobCard.locator(".recruiter-name, .hr-name, .contact-name, [class*='recruiter-name'], [class*='hr-name']");
                    if (hrNameElement.count() > 0) {
                        recruiterName = hrNameElement.first().textContent();
                    } else {
                        recruiterName = "HR";
                    }
                } catch (Exception e) {
                    log.error("获取HR名字失败: {}", e.getMessage());
                    recruiterName = "HR";
                }
                
            } catch (Exception e) {
                log.error("处理岗位卡片失败: {}", e.getMessage());
                continue;
            }
            
            // 查找聊一聊按钮
            Locator button = null;
            String buttonText = "";
            try {
                // 在当前岗位卡片中查找按钮，尝试多种选择器
                Locator currentJobCard = page.locator(JOB_CARDS).nth(i);
                
                String[] buttonSelectors = {
                    "button.ant-btn.ant-btn-primary.ant-btn-round",
                    "button.ant-btn.ant-btn-round.ant-btn-primary", 
                    "button[class*='ant-btn'][class*='primary']",
                    "button[class*='ant-btn'][class*='round']",
                    "button[class*='chat'], button[class*='talk']",
                    ".chat-btn, .talk-btn, .contact-btn",
                    "button:has-text('聊一聊')",
                    "button" // 最后尝试所有按钮
                };
                
                for (String selector : buttonSelectors) {
                    try {
                        Locator tempButtons = currentJobCard.locator(selector);
                        int buttonCount = tempButtons.count();
                        log.debug("选择器 '{}' 找到 {} 个按钮", selector, buttonCount);
                        
                        for (int j = 0; j < buttonCount; j++) {
                            Locator tempButton = tempButtons.nth(j);
                            try {
                                if (tempButton.isVisible()) {
                                    String text = tempButton.textContent();
                                    log.debug("按钮文本: '{}'", text);
                                    if (text != null && !text.trim().isEmpty()) {
                                        button = tempButton;
                                        buttonText = text.trim();
                                        // 只关注"聊一聊"按钮
                                        if (text.contains("聊一聊")) {
                                            log.debug("找到目标按钮: '{}'", text);
                                            break;
                                        }
                                    }
                                }
                            } catch (Exception ignore) {
                                log.debug("获取按钮文本失败: {}", ignore.getMessage());
                            }
                        }
                        
                        if (button != null && buttonText.contains("聊一聊")) {
                            break;
                        }
                    } catch (Exception e) {
                        log.debug("选择器 '{}' 查找失败: {}", selector, e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                log.error("查找按钮失败: {}", e.getMessage());
                // 保存页面源码用于调试
                savePageSource(page, "button_search_failed");
                continue;
            }
            
            // 检查按钮文本并点击
            if (button != null && buttonText.contains("聊一聊")) {
                try {
                    // 在点击按钮前进行鼠标微调，先向右移动2像素，再向左移动2像素
                    try {
                        var boundingBox = button.boundingBox();
                        if (boundingBox != null) {
                            double centerX = boundingBox.x + boundingBox.width / 2;
                            double centerY = boundingBox.y + boundingBox.height / 2;
                            
                            // 先移动到按钮中心
                            page.mouse().move(centerX, centerY);
                            Thread.sleep(50);
                            
                            // 向右移动2像素
                            page.mouse().move(centerX + 2, centerY);
                            Thread.sleep(50);
                            
                            // 向左移动2像素（回到中心再向左2像素）
                            page.mouse().move(centerX - 2, centerY);
                            Thread.sleep(50);
                            
                            // 回到中心位置
                            page.mouse().move(centerX, centerY);
                            Thread.sleep(50);
                            
                            log.debug("完成鼠标微调，准备点击按钮");
                        }
                    } catch (Exception moveError) {
                        log.warn("鼠标微调失败，直接点击按钮: {}", moveError.getMessage());
                    }
                    
                    button.click();
                    // PlaywrightUtil.sleep(1); // 等待点击响应
                    
                    // 猎聘会自动发送打招呼语，所以我们只需要关闭聊天窗口
                    try {
                        // 等待聊天界面加载
                        page.waitForSelector(CHAT_HEADER, new Page.WaitForSelectorOptions().setTimeout(3000));
                        
                        // 直接关闭聊天窗口
                        Locator close = page.locator(CHAT_CLOSE);
                        if (close.count() > 0) {
                            PlaywrightUtil.sleep(1);
                            close.click();
                        }
                        
                        resultList.add(sb.append("【").append(companyName).append(" ").append(jobName).append(" ").append(salary).append(" ").append(recruiterName).append(" ").append("】").toString());
                        sb.setLength(0);
                        log.info("成功发起聊天:【{}】的【{}·{}】岗位", companyName, jobName, salary);
                        
                    } catch (Exception e) {
                        log.warn("关闭聊天窗口失败，但投递可能已成功: {}", e.getMessage());
                        // 即使关闭失败，也认为投递成功
                        resultList.add(sb.append("【").append(companyName).append(" ").append(jobName).append(" ").append(salary).append(" ").append(recruiterName).append(" ").append("】").toString());
                        sb.setLength(0);
                    }
                    
                } catch (Exception e) {
                    log.error("点击按钮失败: {}", e.getMessage());
                    // 保存页面源码用于调试
                    savePageSource(page, "button_click_failed");
                }
            } else {
                if (button != null) {
                    log.debug("跳过岗位（按钮文本不匹配）: 【{}】的【{}·{}】岗位，按钮文本: '{}'", companyName, jobName, salary, buttonText);
                } else {
//                    log.warn("未找到可点击的按钮: 【{}】的【{}·{}】岗位", companyName, jobName, salary);
                    // 保存页面源码用于调试
                    savePageSource(page, "no_button_found");
                }
            }
            
            // 等待一下，避免操作过快
            // PlaywrightUtil.sleep(1);
        }
    }

    @SneakyThrows
    private static void login() {
        log.info("正在打开猎聘网站...");
        Page page = PlaywrightUtil.getPageObject();
        page.navigate(homeUrl);
        log.info("猎聘正在登录...");
        
        if (PlaywrightUtil.isCookieValid(cookiePath)) {
            PlaywrightUtil.loadCookies(cookiePath);
            page.reload();
        }
        
        page.waitForSelector(HEADER_LOGO, new Page.WaitForSelectorOptions().setTimeout(10000));
        
        if (isLoginRequired()) {
            log.info("cookie失效，尝试扫码登录...");
            scanLogin();
            PlaywrightUtil.saveCookies(cookiePath);
        } else {
            log.info("cookie有效，准备投递...");
        }
    }

    private static boolean isLoginRequired() {
        Page page = PlaywrightUtil.getPageObject();
        String currentUrl = page.url();
        return !currentUrl.contains("c.liepin.com");
    }

    private static void scanLogin() {
        try {
            Page page = PlaywrightUtil.getPageObject();
            
            // 点击切换登录类型按钮
            Locator switchBtn = page.locator(LOGIN_SWITCH_BTN);
            if (switchBtn.count() > 0) {
                switchBtn.click();
            }
            
            log.info("等待扫码..");

            // 记录开始时间
            long startTime = System.currentTimeMillis();
            long maxWaitTime = 10 * 60 * 1000; // 10分钟，单位毫秒

            // 主循环，直到登录成功或超时
            while (true) {
                try {
                    // 检查是否已登录
                    Locator loginButtons = page.locator(LOGIN_BUTTONS);
                    if (loginButtons.count() > 0) {
                        String login = loginButtons.first().textContent();
                        if (!login.contains("登录")) {
                            log.info("用户扫码成功，继续执行...");
                            break;
                        }
                    }
                } catch (Exception ignored) {
                    try {
                        Locator userInfo = page.locator(USER_INFO);
                        if (userInfo.count() > 0) {
                            String login = userInfo.first().textContent();
                            if (login.contains("你好")){
                                break;
                            }
                        }
                    } catch (Exception e) {
                        log.error("获取登录状态失败！");
                    }
                }

                // 检查是否超过最大等待时间
                long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime > maxWaitTime) {
                    log.error("登录超时，10分钟内未完成扫码登录，程序将退出。");
                    PlaywrightUtil.close(); // 关闭浏览器
                    return; // 返回而不是退出整个程序
                }
                PlaywrightUtil.sleep(1);
            }

            // 登录成功后，保存Cookie
            PlaywrightUtil.saveCookies(cookiePath);
            log.info("登录成功，Cookie已保存。");

        } catch (Exception e) {
            log.error("scanLogin() 失败: {}", e.getMessage());
            PlaywrightUtil.close(); // 关闭浏览器
            return; // 返回而不是退出整个程序
        }
    }



}
