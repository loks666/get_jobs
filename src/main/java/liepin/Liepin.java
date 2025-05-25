package liepin;

import com.microsoft.playwright.*;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import utils.JobUtils;
import utils.PlaywrightUtil;

import java.util.*;

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

    /**
     * 排除的公司名
     */
    private static final Set<String> EXCLUDE_COMPANY_SET = new HashSet<>();

    /**
     * 包含的工作名
     */
    private static final Set<String> CONTAINS_JOB_NAME = new HashSet<>();

    /**
     * 排除的工作名
     */
    private static final Set<String> EXCLUDE_JOB_NAME = new HashSet<>();

    public static void main(String[] args) {
        PlaywrightUtil.init();
        startDate = new Date();
        login();
        String containsJobName;
        String excludeCompany;
        String excludeJobName;
        if (StringUtils.hasLength((excludeCompany = config.getExcludeCompany()))) {
            EXCLUDE_COMPANY_SET.addAll(List.of(excludeCompany.split(",")));
        }
        if (StringUtils.hasLength(containsJobName = config.getContainsJobName())){
            CONTAINS_JOB_NAME.addAll(List.of(containsJobName.split(",")));
        }
        if (StringUtils.hasLength((excludeJobName = config.getExcludeJobName()))){
            EXCLUDE_JOB_NAME.addAll(List.of(excludeJobName.split(",")));
        }
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
        PlaywrightUtil.navigate(getSearchUrl() + "&key=" + keyword);
        PlaywrightUtil.waitForElement(".list-pagination-box");
        Locator div = PlaywrightUtil.findElement(".list-pagination-box");
        List<Locator> lis = div.locator("li").all();
        setMaxPage(lis);
        for (int i = 0; i < maxPage; i++) {
            try {
                PlaywrightUtil.findElement("//div[contains(@class, 'subscribe-close-btn')]").click();
            } catch (Exception ignored) {
            }
            PlaywrightUtil.waitForElement("//div[contains(@class, 'job-card-pc-container')]");
            log.info("正在投递【{}】第【{}】页...", keyword, i + 1);
            submitJob();
            log.info("已投递第【{}】页所有的岗位...\n", i + 1);
            div = PlaywrightUtil.findElement(".list-pagination-box");
            Locator nextPage = div.locator(".//li[@title='Next Page']");
            if (nextPage.getAttribute("disabled") == null) {
                nextPage.click();
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
                "&currentPage=" + 0 + "&dq=" + config.getCityCode();
    }


    private static void setMaxPage(List<Locator> lis) {
        try {
            int page = Integer.parseInt(lis.get(lis.size() - 2).textContent());
            if (page > 1) {
                maxPage = page;
            }
        } catch (Exception ignored) {
        }
    }

    private static void submitJob() {
        // 获取hr数量
        String getRecruiters = "//div[contains(@class, 'job-card-pc-container')]";
        Locator recruitersLocator = PlaywrightUtil.findElement(getRecruiters);
        int count = recruitersLocator.count();
        StringBuilder sb = new StringBuilder();
        Page page = PlaywrightUtil.getPageObject();
        
        for (int i = 0; i < count; i++) {
            page.evaluate("window.scrollBy(0,120);");

            Locator jobTitleBoxes = PlaywrightUtil.findElement("//div[contains(@class, 'job-title-box')]");
            Locator companyNames = PlaywrightUtil.findElement("//span[contains(@class, 'company-name')]");
            Locator jobSalaries = PlaywrightUtil.findElement("//span[contains(@class, 'job-salary')]");
            
            String jobName = jobTitleBoxes.nth(i).textContent().replaceAll("\n", " ").replaceAll("【 ", "[").replaceAll(" 】", "]");
            String companyName = companyNames.nth(i).textContent().replaceAll("\n", " ");
            String salary = jobSalaries.nth(i).textContent().replaceAll("\n", " ");
            String recruiterName = null;
            
            if (EXCLUDE_COMPANY_SET.stream().anyMatch(companyName::contains)){
                log.info("命中已排除公司：{}", companyName);
                continue;
            }
            if (CONTAINS_JOB_NAME.stream().noneMatch(jobName::contains)){
                log.info("命中未包含的工作名：{}",jobName);
                continue;
            }
            if (EXCLUDE_JOB_NAME.stream().anyMatch(jobName::contains)){
                log.info("命中排除的工作名: {}",jobName);
                continue;
            }
            try {
                // 获取hr名字
                Locator recruiters = PlaywrightUtil.findElement(getRecruiters);
                Locator name = recruiters.nth(i);
                recruiterName = name.textContent();
            } catch (Exception e) {
                log.error(e.getMessage());
            }
            try {
                // 移动到hr标签处
                Locator name = PlaywrightUtil.findElement("//div[contains(@class, 'job-card-pc-container')]").nth(i);
                name.hover();
            } catch (Exception ignore) {
            }
            Locator button;
            try {
                button = PlaywrightUtil.findElement("//button[@class='ant-btn ant-btn-primary ant-btn-round']");
            } catch (Exception e) {
                //嵌套一个异常，用来获取对应按钮
                try {
                    button = PlaywrightUtil.findElement("//button[@Class='ant-btn ant-btn-round ant-btn-primary']");
                } catch (Exception e1) {
                    continue;
                }
            }
            String text;
            try {
                text = button.textContent();
            } catch (Exception ignore) {
                text = "";
            }
            if (text.contains("聊一聊")) {
                try {
                    button.click();
                } catch (Exception ignore) {
                }
                PlaywrightUtil.waitForElement(".__im_basic__header-wrap");
                PlaywrightUtil.waitForElement("//textarea[contains(@class, '__im_basic__textarea')]");
                Locator input = PlaywrightUtil.findElement("//textarea[contains(@class, '__im_basic__textarea')]");
                input.click();
                PlaywrightUtil.sleep(1);
                Locator close = PlaywrightUtil.findElement("div.__im_basic__contacts-title svg");
                close.click();
                PlaywrightUtil.waitForElement("//div[contains(@class, 'recruiter-info-box')]");

                resultList.add(sb.append("【").append(companyName).append(" ").append(jobName).append(" ").append(salary).append(" ").append(recruiterName).append(" ").append("】").toString());
                sb.setLength(0);
                log.info("发起新聊天:【{}】的【{}·{}】岗位", companyName, jobName, salary);
            }
            // 移动鼠标偏移，避免hover效果
            Mouse mouse = page.mouse();
            mouse.move(125, 0);
        }
    }

    @SneakyThrows
    private static void login() {
        log.info("正在打开猎聘网站...");
        PlaywrightUtil.navigate(homeUrl);
        log.info("猎聘正在登录...");
        if (isCookieValid(cookiePath)) {
            PlaywrightUtil.loadCookies(cookiePath);
            PlaywrightUtil.getPageObject().reload();
        }
        PlaywrightUtil.waitForElement("#header-logo-box");
        if (isLoginRequired()) {
            log.info("cookie失效，尝试扫码登录...");
            scanLogin();
            PlaywrightUtil.saveCookies(cookiePath);
        } else {
            log.info("cookie有效，准备投递...");
        }
    }

    private static boolean isLoginRequired() {
        String currentUrl = PlaywrightUtil.getUrl();
        return !currentUrl.contains("c.liepin.com");
    }

    private static void scanLogin() {
        try {
            // 点击切换登录类型按钮
            PlaywrightUtil.click("//div[@class='jsx-263198893 btn-sign-switch']");
            log.info("等待扫码..");
            boolean isLoggedIn = false;

            // 记录开始时间
            long startTime = System.currentTimeMillis();
            long maxWaitTime = 10 * 60 * 1000; // 10分钟，单位毫秒

            // 主循环，直到登录成功或超时
            while (true) {
                try {
                    // 检查是否已登录
                    Locator loginButtons = PlaywrightUtil.findElement("//button[@type='button']");
                    String login = loginButtons.first().textContent();

                    if (!login.contains("登录")) {
                        log.info("用户扫码成功，继续执行...");
                        break;
                    }
                } catch (Exception ignored) {
                    try {
                        Locator userInfo = PlaywrightUtil.findElement("//div[@id='header-quick-menu-user-info']");
                        String login = userInfo.first().textContent();
                        if (login.contains("你好")){
                            break;
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

    private static boolean isCookieValid(String cookiePath) {
        try {
            String cookieContent = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(cookiePath)));
            return cookieContent != null && !cookieContent.equals("[]") && cookieContent.contains("name");
        } catch (Exception e) {
            log.error("读取cookie文件失败: {}", e.getMessage());
            return false;
        }
    }


}
