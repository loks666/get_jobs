package lagou;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.KeyboardModifier;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JobUtils;
import utils.PlaywrightUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static utils.Bot.sendMessageByTime;
import static utils.JobUtils.formatDuration;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
public class Lagou {
    private static final Logger log = LoggerFactory.getLogger(Lagou.class);

    static Integer page = 1;
    static Integer maxPage = 4;
    static String homeUrl = "https://www.lagou.com?";
    static String wechatUrl = "https://open.weixin.qq.com/connect/qrconnect?appid=wx9d8d3686b76baff8&redirect_uri=https%3A%2F%2Fpassport.lagou.com%2Foauth20%2Fcallback_weixinProvider.html&response_type=code&scope=snsapi_login#wechat_redirect";
    static int oneKeyMaxJob = 20;
    static int currentKeyJobNum = 0;
    static int jobCount = 0;
    static String cookiePath = "./src/main/java/lagou/cookie.json";
    static LagouConfig config = LagouConfig.init();
    static Date startDate;


    public static void main(String[] args) {
        PlaywrightUtil.init();
        startDate = new Date();
        login();
        PlaywrightUtil.navigate(homeUrl);
        homeUrl = "https://www.lagou.com/wn/zhaopin?fromSearch=true";
        config.getKeywords().forEach(keyword -> {
            String searchUrl = getSearchUrl(keyword);
            PlaywrightUtil.navigate(searchUrl);
            setMaxPage();
            for (int i = page; i <= maxPage || currentKeyJobNum > oneKeyMaxJob; i++) {
                submit();
                try {
                    getWindow();
                    PlaywrightUtil.findElement(".lg-pagination-item-link").nth(1).click();
                } catch (Exception e) {
                    break;
                }
            }
            currentKeyJobNum = 0;
        });
        printResult();
    }

    private static void printResult() {
        String message = String.format("\n拉勾投递完成，共投递%d个岗位，用时%s", jobCount, formatDuration(startDate, new Date()));
        log.info(message);
        sendMessageByTime(message);
        jobCount = 0;
        PlaywrightUtil.close();
    }

    private static String getSearchUrl(String keyword) {
        return homeUrl +
                JobUtils.appendParam("city", config.getCityCode()) +
                JobUtils.appendParam("kd", keyword) +
                JobUtils.appendParam("yx", config.getSalary()) +
                JobUtils.appendParam("gj", config.getGj()) +
                JobUtils.appendListParam("gm", config.getScale());
    }

    /**
     * 设置选项
     */
    private static void setMaxPage() {
        // 模拟 Ctrl + End
        PlaywrightUtil.getPageObject().keyboard().press("Control+End");
        Locator secondLastLi = PlaywrightUtil.findElement("(//ul[@class='lg-pagination']/li)[last()-1]");
        if (secondLastLi != null && secondLastLi.textContent().matches("\\d+")) {
            maxPage = Integer.parseInt(secondLastLi.textContent());
        }
        // 模拟 Ctrl + Home
        PlaywrightUtil.getPageObject().keyboard().press("Control+Home");
    }

    @SneakyThrows
    private static void submit() {
        // 获取所有的元素
        Locator elements = null;
        try {
            PlaywrightUtil.getPageObject().keyboard().press("Home");
            PlaywrightUtil.sleep(1);
            PlaywrightUtil.waitForElement("#openWinPostion");
            elements = PlaywrightUtil.findElement("#openWinPostion");

        } catch (Exception ignore) {
        }
        if (elements != null) {
            int elementCount = elements.count();
            for (int i = 0; i < elementCount || currentKeyJobNum > oneKeyMaxJob; i++) {
                Locator element = null;
                try {
                    element = elements.nth(i);
                } catch (Exception e) {
                    log.error("获取岗位列表中某个岗位失败，岗位列表数量：{},获取第【{}】个元素失败", i + 1, elementCount);
                }
                try {
                    element.hover();
                } catch (Exception e) {
                    getWindow();
                }
                if (-1 == tryClick(element, i)) {
                    continue;
                }
                TimeUnit.SECONDS.sleep(1);
                getWindow();
                String jobName;
                Locator submit;
                try {
                    jobName = PlaywrightUtil.findElement(".header__HY1Cm").textContent();
                } catch (Exception e) {
                    try {
                        jobName = PlaywrightUtil.findElement(".position-head-wrap-position-name").textContent();
                    } catch (Exception ex) {
                        PlaywrightUtil.sleep(10);
                        continue;
                    }

                }
                if (!(jobName != null && !jobName.isEmpty() && !jobName.contains("销"))) {
                    closeTab();
                    getWindow();
                    continue;
                }
                submit = PlaywrightUtil.findElement(".resume-deliver");
                if ("投简历".equals(submit.textContent())) {
                    String jobTitle = null;
                    String companyName = null;
                    String jobInfo = null;
                    String companyInfo = null;
                    String salary = null;
                    String weal = null;
                    try {
                        jobTitle = PlaywrightUtil.findElement("span.name__36WTQ").textContent();
                        companyName = PlaywrightUtil.findElement("span.company").textContent();
                        jobInfo = PlaywrightUtil.findElement("h3.position-tags span").all()
                                .stream()
                                .map(Locator::textContent)
                                .collect(Collectors.joining("/"));
                        companyInfo = PlaywrightUtil.findElement("div.header__HY1Cm").textContent();
                        salary = PlaywrightUtil.findElement("span.salary__22Kt_").textContent();
                        weal = PlaywrightUtil.findElement("li.labels").textContent();
                    } catch (Exception e) {
                        log.error("获取职位信息失败", e);
                        try {
                            jobTitle = PlaywrightUtil.findElement("span.position-head-wrap-position-name").textContent();
                            companyName = PlaywrightUtil.findElement("span.company").textContent();
                            List<Locator> jobInfoElements = PlaywrightUtil.findElement("h3.position-tags span:not(.tag-point)").all();
                            jobInfo = jobInfoElements.stream()
                                    .map(Locator::textContent)
                                    .collect(Collectors.joining("/"));
                            companyInfo = PlaywrightUtil.findElement("span.company").textContent();
                            salary = PlaywrightUtil.findElement("span.salary").textContent();
                            weal = PlaywrightUtil.findElement("dd.job-advantage p").textContent();
                        } catch (Exception ex) {
                            log.error("第二次获取职位信息失败，放弃了！", ex);
                        }
                    }
                    log.info("投递: {},职位: {},公司: {},职位信息: {},公司信息: {},薪资: {},福利: {}", jobTitle, jobTitle, companyName, jobInfo, companyInfo, salary, weal);
                    jobCount++;
                    currentKeyJobNum++;
                    TimeUnit.SECONDS.sleep(2);
                    submit.click();
                    TimeUnit.SECONDS.sleep(2);
                    try {
                        Locator send = PlaywrightUtil.findElement("body > div:nth-child(45) > div > div.lg-design-modal-wrap.position-modal > div > div.lg-design-modal-content > div.lg-design-modal-footer > button.lg-design-btn.lg-design-btn-default");
                        if ("确认投递".equals(send.textContent())) {
                            send.click();
                        }
                    } catch (Exception e) {
                        log.error("没有【确认投递】的弹窗，继续！");
                    }
                    try {
                        Locator confirm = PlaywrightUtil.findElement("button.lg-design-btn.lg-design-btn-primary span");
                        String buttonText = confirm.textContent();
                        if ("我知道了".equals(buttonText)) {
                            confirm.click();
                        } else {
                            TimeUnit.SECONDS.sleep(1);
                        }
                    } catch (Exception e) {
                        log.error("第一次点击【我知道了】按钮失败...重试xpath点击...");
                        TimeUnit.SECONDS.sleep(1);
                        try {
                            PlaywrightUtil.findElement("/html/body/div[7]/div/div[2]/div/div[2]/div[2]/button[2]").click();
                        } catch (Exception ex) {
                            log.error("第二次点击【我知道了】按钮失败...放弃了！", ex);
                            TimeUnit.SECONDS.sleep(10);
                            PlaywrightUtil.getPageObject().reload();
                        }
                    }
                    try {
                        TimeUnit.SECONDS.sleep(2);
                        PlaywrightUtil.findElement("#__next > div:nth-child(3) > div > div > div.feedback_job__3EnWp > div.feedback_job_title__2y8Bj > div.feedback_job_deliver__3UIB5.feedback_job_active__3bbLa").click();
                    } catch (Exception e) {
                        log.error("这个岗位没有推荐职位...");
                        TimeUnit.SECONDS.sleep(1);
                    }
                } else if ("立即沟通".equals(submit.textContent())) {
                    submit.click();
                    try {
                        PlaywrightUtil.waitForElement("//*[@id=\"modalConIm\"]").click();
                    } catch (Exception e) {
                        submit.click();
                        PlaywrightUtil.waitForElement("//*[@id=\"modalConIm\"]").click();
                    }
                } else {
                    log.info("这个岗位没有投简历按钮...一秒后关闭标签页面！");
                    TimeUnit.SECONDS.sleep(1);
                }
                closeTab();
                getWindow();
            }
        }
    }

    private static void closeTab() {
        Page page = PlaywrightUtil.getPageObject();
        BrowserContext context = page.context();
        List<Page> pages = context.pages();
        if (pages.size() > 1) {
            pages.get(pages.size() - 1).close();
        }
    }

    private static void getWindow() {
        try {
            Page page = PlaywrightUtil.getPageObject();
            BrowserContext context = page.context();
            List<Page> pages = context.pages();
            if (pages.size() > 1) {
                pages.get(pages.size() - 1).bringToFront();
            }
        } catch (Exception ignore) {
        }
    }

    private static int tryClick(Locator element, int i) {
        try {
            // Ctrl+Click 在新标签页打开
            Page page = PlaywrightUtil.getPageObject();
            page.keyboard().down("Control");
            element.click();
            page.keyboard().up("Control");
            return 0;
        } catch (Exception e) {
            try {
                PlaywrightUtil.findElement("#openWinPostion").nth(i).click(new Locator.ClickOptions().setModifiers(List.of(KeyboardModifier.CONTROL)));
                return 0;
            } catch (Exception ex) {
                log.info(ex.getMessage());
                return -1;
            }
        }
    }

    @SneakyThrows
    private static void newTab(int index) {
        Page currentPage = PlaywrightUtil.getPageObject();
        String company = PlaywrightUtil.findElement(".company-name__2-SjF a").textContent();
        String jobTitle = PlaywrightUtil.findElement(".p-top__1F7CL a").textContent();
        
        // Ctrl+Click 在新标签页打开
        currentPage.keyboard().down("Control");
        PlaywrightUtil.findElement("#openWinPostion").nth(index).click();
        currentPage.keyboard().up("Control");
        
        // 等待新页面加载
        BrowserContext context = currentPage.context();
        List<Page> pages = context.pages();
        Page newPage = pages.get(pages.size() - 1);
        newPage.bringToFront();
        
        newPage.waitForSelector(".resume-deliver");

        if (!"已投递".equals(newPage.locator(".resume-deliver").first().textContent())) {
            newPage.locator(".resume-deliver").first().click();
            TimeUnit.SECONDS.sleep(1);
            newPage.waitForSelector("button.lg-design-btn.lg-design-btn-primary").click();
            log.info("投递【{}】公司: 【{}】岗位", company, jobTitle);
        }
        newPage.close();
        currentPage.bringToFront();
    }

    @SneakyThrows
    private static void login() {
        log.info("正在打开拉勾...");
        PlaywrightUtil.navigate("https://www.lagou.com");
        log.info("拉勾正在登录...");
        if (isCookieValid(cookiePath)) {
            PlaywrightUtil.loadCookies(cookiePath);
            PlaywrightUtil.getPageObject().reload();
        }
        PlaywrightUtil.waitForElement("#search_button");
        if (isLoginRequired()) {
            log.info("cookie失效，尝试扫码登录...");
            scanLogin();
            PlaywrightUtil.saveCookies(cookiePath);
        } else {
            log.info("cookie有效，准备投递...");
        }
    }

    private static boolean isLoginRequired() {
        try {
            Locator header = PlaywrightUtil.findElement("#lg_tbar");
            return header.textContent().contains("登录");
        } catch (Exception e) {
            return true;
        }
    }

    private static void scanLogin() {
        try {
            PlaywrightUtil.navigate(wechatUrl);
            log.info("等待扫码..");
            PlaywrightUtil.waitForElement("#search_button", 300000); // 等待5分钟
        } catch (Exception e) {
            PlaywrightUtil.getPageObject().reload();
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
