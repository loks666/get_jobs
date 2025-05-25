package zhilian;

import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Job;
import utils.JobUtils;
import utils.PlaywrightUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static utils.Bot.sendMessageByTime;
import static utils.JobUtils.formatDuration;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
public class ZhiLian {
    private static final Logger log = LoggerFactory.getLogger(ZhiLian.class);
    static String loginUrl = "https://passport.zhaopin.com/login";
    static String homeUrl = "https://sou.zhaopin.com/?";
    static boolean isLimit = false;
    static int maxPage = 500;
    static ZhilianConfig config = ZhilianConfig.init();
    static List<Job> resultList = new ArrayList<>();
    static Date startDate;
    static String cookiePath = "./src/main/java/zhilian/cookie.json";

    public static void main(String[] args) {
        PlaywrightUtil.init();
        startDate = new Date();
        login();
        config.getKeywords().forEach(keyword -> {
            if (isLimit) {
                return;
            }
            PlaywrightUtil.navigate(getSearchUrl(keyword, 1));
            submitJobs(keyword);

        });
        log.info(resultList.isEmpty() ? "未投递新的岗位..." : "新投递公司如下:\n{}", resultList.stream().map(Object::toString).collect(Collectors.joining("\n")));
        printResult();
    }

    private static void printResult() {
        String message = String.format("\n智联招聘投递完成，共投递%d个岗位，用时%s", resultList.size(), formatDuration(startDate, new Date()));
        log.info(message);
        sendMessageByTime(message);
        resultList.clear();
        PlaywrightUtil.close();
    }

    private static String getSearchUrl(String keyword, int page) {
        return homeUrl +
                JobUtils.appendParam("jl", config.getCityCode()) +
                JobUtils.appendParam("kw", keyword) +
                JobUtils.appendParam("sl", config.getSalary()) +
                "&p=" + page;
    }

    private static void submitJobs(String keyword) {
        if (isLimit) {
            return;
        }
        PlaywrightUtil.waitForElement("//div[contains(@class, 'joblist-box__item')]");
        setMaxPages();
        for (int i = 1; i <= maxPage; i++) {
            if (i != 1) {
                PlaywrightUtil.navigate(getSearchUrl(keyword, i));
            }
            log.info("开始投递【{}】关键词，第【{}】页...", keyword, i);
            // 等待岗位出现
            try {
                PlaywrightUtil.waitForElement("//div[@class='positionlist']");
            } catch (Exception ignore) {
                PlaywrightUtil.getPageObject().reload();
                PlaywrightUtil.sleep(1);
            }
            // 全选
            try {
                Locator allSelect = PlaywrightUtil.waitForElement("//i[@class='betch__checkall__checkbox']");
                allSelect.click();
            } catch (Exception e) {
                log.info("没有全选按钮，程序退出...");
                continue;
            }
            // 投递
            Locator submit = PlaywrightUtil.waitForElement("//button[@class='betch__button']");
            submit.click();
            if (checkIsLimit()) {
                break;
            }
            PlaywrightUtil.sleep(1);
            // 切换到新的标签页
            Page page = PlaywrightUtil.getPageObject();
            BrowserContext context = page.context();
            List<Page> pages = context.pages();
            if (pages.size() > 1) {
                pages.get(pages.size() - 1).bringToFront();
            }
            //关闭弹框
            try {
                Locator result = PlaywrightUtil.findElement("//div[@class='deliver-dialog']");
                if (result.textContent().contains("申请成功")) {
                    log.info("岗位申请成功！");
                }
            } catch (Exception e) {
                log.error("关闭投递弹框失败...");
            }
            try {
                Locator close = PlaywrightUtil.findElement("//img[@title='close-icon']");
                close.click();
            } catch (Exception e) {
                if (checkIsLimit()) {
                    break;
                }
            }
            try {
                // 投递相似职位
                Locator checkButton = PlaywrightUtil.findElement("//div[contains(@class, 'applied-select-all')]//input");
                if (!checkButton.isChecked()) {
                    checkButton.click();
                }
                Locator jobs = PlaywrightUtil.findElement("//div[@class='recommend-job']");
                Locator post = PlaywrightUtil.findElement("//div[contains(@class, 'applied-select-all')]//button");
                post.click();
                printRecommendJobs(jobs);
                log.info("相似职位投递成功！");
            } catch (PlaywrightException e) {
                log.error("没有匹配到相似职位...");
            } catch (Exception e) {
                log.error("相似职位投递异常！！！");
            }
            // 投完了关闭当前窗口并切换至第一个窗口
            if (pages.size() > 1) {
                pages.get(pages.size() - 1).close();
                pages.get(0).bringToFront();
            }
        }
    }

    private static boolean checkIsLimit() {
        try {
            PlaywrightUtil.sleepMillis(500);
            Locator result = PlaywrightUtil.findElement("//div[@class='a-job-apply-workflow']");
            if (result.textContent().contains("达到上限")) {
                log.info("今日投递已达上限！");
                isLimit = true;
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static void setMaxPages() {
        try {
            Page page = PlaywrightUtil.getPageObject();
            // 到底部
            page.keyboard().press("Control+End");
            Locator inputElement = PlaywrightUtil.findElement(".soupager__pagebox__goinp");
            inputElement.clear();
            inputElement.fill("99999");
            //使用 JavaScript 获取输入元素的当前值
            String modifiedValue = (String) page.evaluate("() => document.querySelector('.soupager__pagebox__goinp').value");
            maxPage = Integer.parseInt(modifiedValue);
            log.info("设置最大页数：{}", maxPage);
            Locator home = PlaywrightUtil.findElement("//li[@class='listsort__item']");
            home.hover();
        } catch (Exception ignore) {
            StackTraceElement element = Thread.currentThread().getStackTrace()[1];
            log.info("setMaxPages@设置最大页数异常！({}:{})", element.getFileName(), element.getLineNumber());
            log.info("设置默认最大页数50，如有需要请自行调整...");
            maxPage = 50;
        }
    }

    private static void printRecommendJobs(Locator jobsLocator) {
        int jobCount = jobsLocator.count();
        for (int i = 0; i < jobCount; i++) {
            Locator job = jobsLocator.nth(i);
            String jobName = job.locator(".//*[contains(@class, 'recommend-job__position')]").textContent();
            String salary = job.locator(".//span[@class='recommend-job__demand__salary']").textContent();
            String years = job.locator(".//span[@class='recommend-job__demand__experience']").textContent().replaceAll("\n", " ");
            String education = job.locator(".//span[@class='recommend-job__demand__educational']").textContent().replaceAll("\n", " ");
            String companyName = job.locator(".//*[contains(@class, 'recommend-job__cname')]").textContent();
            String companyTag = job.locator(".//*[contains(@class, 'recommend-job__demand__cinfo')]").textContent().replaceAll("\n", " ");
            Job jobInfo = new Job();
            jobInfo.setJobName(jobName);
            jobInfo.setSalary(salary);
            jobInfo.setCompanyTag(companyTag);
            jobInfo.setCompanyName(companyName);
            jobInfo.setJobInfo(years + "·" + education);
            log.info("投递【{}】公司【{}】岗位，薪资【{}】，要求【{}·{}】，规模【{}】", companyName, jobName, salary, years, education, companyTag);
            resultList.add(jobInfo);
        }
    }

    private static void login() {
        PlaywrightUtil.navigate(loginUrl);
        if (isCookieValid(cookiePath)) {
            PlaywrightUtil.loadCookies(cookiePath);
            PlaywrightUtil.getPageObject().reload();
            PlaywrightUtil.sleep(1);
        }
        if (isLoginRequired()) {
            scanLogin();
        }
    }

    private static void scanLogin() {
        try {
            Locator button = PlaywrightUtil.findElement("//div[@class='zppp-panel-normal-bar__img']");
            button.click();
            log.info("等待扫码登录中...");
            PlaywrightUtil.waitForElement("//div[@class='zp-main__personal']", 300000); // 5分钟超时
            log.info("扫码登录成功！");
            PlaywrightUtil.saveCookies(cookiePath);
        } catch (Exception e) {
            log.error("扫码登录异常！");
            System.exit(-1);
        }
    }

    private static boolean isLoginRequired() {
        return !PlaywrightUtil.getUrl().contains("i.zhaopin.com");
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
