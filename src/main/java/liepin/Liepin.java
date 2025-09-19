package liepin;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import utils.JobUtils;
import utils.PlaywrightUtil;
import utils.SeleniumUtil;

import java.util.*;

import static liepin.Locators.*;
import static utils.Bot.sendMessageByTime;
import static utils.JobUtils.formatDuration;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
public class Liepin {
    private static final Logger log = LoggerFactory.getLogger(Liepin.class);
    static String homeUrl = "https://www.liepin.com/";
    static String cookiePath = "./src/main/java/liepin/cookie.json";
    static int maxPage = 50;
    static List<String> resultList = new ArrayList<>();
    static String baseUrl = "https://www.liepin.com/zhaopin/?";
    static LiepinConfig config = LiepinConfig.init();
    static Date startDate;



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
                PlaywrightUtil.sleep(2); // 等待页面加载
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
        
        // 获取hr数量
        Locator jobCards = page.locator(JOB_CARDS);
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
                // 获取hr名字 - 在当前岗位卡片中查找HR信息
                Locator currentJobCard = page.locator(JOB_CARDS).nth(i);
                Locator hrNameElement = currentJobCard.locator(".recruiter-name, .hr-name, .contact-name");
                if (hrNameElement.count() > 0) {
                    recruiterName = hrNameElement.first().textContent();
                } else {
                    // 如果找不到特定的HR名字元素，使用默认值
                    recruiterName = "HR";
                }
            } catch (Exception e) {
                log.error("获取HR名字失败: {}", e.getMessage());
                recruiterName = "HR";
            }
            
            try {
                // 移动到hr标签处
                Locator jobCard = page.locator(JOB_CARDS).nth(i);
                jobCard.scrollIntoViewIfNeeded();
            } catch (Exception ignore) {
            }
            
            Locator button = null;
            try {
                // 在当前岗位卡片中查找按钮
                Locator currentJobCard = page.locator(JOB_CARDS).nth(i);
                button = currentJobCard.locator("button.ant-btn.ant-btn-primary.ant-btn-round");
                if (button.count() == 0) {
                    button = currentJobCard.locator("button.ant-btn.ant-btn-round.ant-btn-primary");
                }
            } catch (Exception e) {
                continue;
            }
            
            String text = "";
            try {
                if (button.count() > 0) {
                    text = button.textContent();
                }
            } catch (Exception ignore) {
            }
            
            if (text.contains("聊一聊")) {
                try {
                    button.click();
                } catch (Exception ignore) {
                }
                
                // 等待聊天界面加载
                page.waitForSelector(CHAT_HEADER, new Page.WaitForSelectorOptions().setTimeout(5000));
                page.waitForSelector(CHAT_TEXTAREA, new Page.WaitForSelectorOptions().setTimeout(5000));
                
                Locator input = page.locator(CHAT_TEXTAREA);
                input.click();
                PlaywrightUtil.sleep(1);
                
                Locator close = page.locator(CHAT_CLOSE);
                close.click();
                
                page.waitForSelector(RECRUITER_INFO, new Page.WaitForSelectorOptions().setTimeout(5000));

                resultList.add(sb.append("【").append(companyName).append(" ").append(jobName).append(" ").append(salary).append(" ").append(recruiterName).append(" ").append("】").toString());
                sb.setLength(0);
                log.info("发起新聊天:【{}】的【{}·{}】岗位", companyName, jobName, salary);
            }
            
            // 等待一下，避免操作过快
            PlaywrightUtil.sleep(1);
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
                    System.exit(1); // 超时，退出程序
                }
                PlaywrightUtil.sleep(1);
            }

            // 登录成功后，保存Cookie
            PlaywrightUtil.saveCookies(cookiePath);
            log.info("登录成功，Cookie已保存。");

        } catch (Exception e) {
            log.error("scanLogin() 失败: {}", e.getMessage());
            System.exit(1); // 出现异常时退出程序
        }
    }



}
