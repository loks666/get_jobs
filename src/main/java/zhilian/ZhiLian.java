package zhilian;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Job;
import utils.Platform;
import utils.SeleniumUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static utils.Constant.CHROME_DRIVER;
import static utils.Constant.WAIT;

public class ZhiLian {
    private static final Logger log = LoggerFactory.getLogger(ZhiLian.class);

    static String loginUrl = "https://passport.zhaopin.com/login";

    static String searchUrl = "https://sou.zhaopin.com/?jl=%s&kw=%s&&sl=%s&p=%s";

    static String cityCode = "538";

    static String salaryScope = "25001,35000";

    static List<String> keywords = List.of("Java", "AIGC", "大模型", "Python", "Golang");

    static List<Job> resultList = new ArrayList<>();

    static boolean isLimit = false;

    public static void main(String[] args) {
        SeleniumUtil.initDriver();
        login();
        keywords.forEach(ZhiLian::submitJobs);
        printResult();
        CHROME_DRIVER.quit();
    }

    private static void printResult() {
        log.info("今日投递岗位:\n{}", resultList.stream().map(job -> job.toString(Platform.ZHILIAN)).collect(Collectors.joining("\n")));
        log.info("投递完成,共投递 {} 个岗位！", resultList.size());
    }

    private static void submitJobs(String key) {
        if (isLimit) {
            return;
        }
        CHROME_DRIVER.get(searchUrl.formatted(cityCode, key, salaryScope, "1"));
        CHROME_DRIVER.findElement(By.xpath("//input[@class='soupager__pagebox__goinp']")).sendKeys("500");
        CHROME_DRIVER.findElement(By.xpath("//button[contains(@class, 'soupager__btn soupager__pagebox__gobtn')]")).click();
        String currentUrl = CHROME_DRIVER.getCurrentUrl();
        int pages = getMaxPages(currentUrl);
        for (int i = 1; i <= pages; i++) {
            CHROME_DRIVER.get(searchUrl.formatted(cityCode, key, salaryScope, String.valueOf(i)));
            log.info("开始投递【{}】关键词，第【{}】页...", key, i);
            // 等待岗位出现
            try {
                WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@class='positionlist']")));
            } catch (Exception ignore) {
                CHROME_DRIVER.navigate().refresh();
                SeleniumUtil.sleep(1);
            }
            // 全选
            try {
                WebElement allSelect = WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//i[@class='betch__checkall__checkbox']")));
                allSelect.click();
            } catch (Exception e) {
                log.info("没有全选按钮，程序退出...");
                System.exit(-1);
            }

            // 投递
            WebElement submit = WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@class='a-job-apply-button']")));

            // 保存投递的岗位
            List<Job> positions = getPositionList();
            submit.click();
            SeleniumUtil.sleep(1);
            // 切换到新的标签页
            ArrayList<String> tabs = new ArrayList<>(CHROME_DRIVER.getWindowHandles());
            CHROME_DRIVER.switchTo().window(tabs.get(tabs.size() - 1));
            //关闭弹框
            try {
                WebElement result = CHROME_DRIVER.findElement(By.xpath("//div[@class='deliver-dialog']"));
                if (result.getText().contains("申请成功")) {
                    positions.forEach(job -> log.info("投递【{}】公司【{}】岗位，【{}】地区，薪资【{}】，标签【{}】，HR【{}】", job.getCompanyName(), job.getJobName(), job.getJobArea(), job.getSalary(), job.getCompanyTag(), job.getRecruiter()));
                    resultList.addAll(positions);
                }
            } catch (Exception e) {
                log.error("关闭弹框失败！");
            }
            try {
                WebElement close = CHROME_DRIVER.findElement(By.xpath("//img[@title='close-icon']"));
                close.click();
            } catch (Exception e) {
                try {
                    WebElement result = CHROME_DRIVER.findElement(By.xpath("//div[@class='a-job-apply-workflow']"));
                    if (result.getText().contains("达到上限")) {
                        log.info("今日投递已达上限！");
                        isLimit = true;
                        break;
                    }
                } catch (Exception ignored) {}
            }
            try {
                WebElement checkButton = CHROME_DRIVER.findElement(By.xpath("//div[contains(@class, 'applied-select-all')]//input"));
                if (!checkButton.isSelected()) {
                    checkButton.click();
                }
            } catch (Exception e) {
                log.error("推荐岗位全选框勾选状态判定异常！");
            }
            // 相似职位推荐
            List<WebElement> jobs = CHROME_DRIVER.findElements(By.xpath("//div[@class='recommend-job']"));
            try {
                WebElement post = CHROME_DRIVER.findElement(By.xpath("//div[contains(@class, 'applied-select-all')]//button"));
                post.click();
            } catch (Exception e) {
                log.error("推荐岗位投递异常！");
            }
            printRecommendJobs(jobs);
            WebElement similarResult = WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//span[@class='deliver-title__left__word']")));
            log.info(similarResult.getText().contains("投递成功") ? "推荐职位投递成功！" : "推荐职位投递失败！");
            // 投完了关闭当前窗口并切换至第一个窗口
            CHROME_DRIVER.close();
            CHROME_DRIVER.switchTo().window(tabs.get(0));
        }
    }

    private static int getMaxPages(String url) {
        String[] parts = url.split("&p=", 2);
        return Integer.parseInt(parts[1]);
    }

    private static void printRecommendJobs(List<WebElement> jobs) {
        jobs.forEach(j -> {
            String jobName = j.findElement(By.xpath(".//*[contains(@class, 'recommend-job__position')]")).getText();
            String salary = j.findElement(By.xpath(".//span[@class='recommend-job__demand__salary']")).getText();
            String years = j.findElement(By.xpath(".//span[@class='recommend-job__demand__experience']")).getText().replaceAll("\n", " ");
            String education = j.findElement(By.xpath(".//span[@class='recommend-job__demand__educational']")).getText().replaceAll("\n", " ");
            String companyName = j.findElement(By.xpath(".//*[contains(@class, 'recommend-job__cname')]")).getText();
            String companyTag = j.findElement(By.xpath(".//*[contains(@class, 'recommend-job__demand__cinfo')]")).getText().replaceAll("\n", " ");
            Job job = new Job();
            job.setJobName(jobName);
            job.setSalary(salary);
            job.setCompanyTag(companyTag);
            job.setCompanyName(companyName);
            job.setJobInfo(years + "·" + education);
            log.info("投递【{}】公司【{}】岗位，薪资【{}】，要求【{}·{}】，规模【{}】", companyName, jobName, salary, years, education, companyTag);
            resultList.add(job);
        });
    }

    private static List<Job> getPositionList() {
        List<WebElement> jobs = CHROME_DRIVER.findElements(By.xpath("//div[@class='joblist-box__item clearfix']"));
        ArrayList<Job> result = new ArrayList<>();
        jobs.forEach(j -> {
            String jobName = j.findElement(By.xpath(".//*[contains(@class, 'jobinfo__name')]")).getText();
            String salary = j.findElement(By.xpath(".//p[@class='jobinfo__salary']")).getText();
            String jobArea = j.findElement(By.xpath(".//div[@class='jobinfo__other-info-item']//span")).getText().replaceAll("\n", " ");
            String companyName = j.findElement(By.xpath(".//div[contains(@class, 'companyinfo__name')]")).getText();
            String companyTag = j.findElement(By.xpath(".//div[contains(@class, 'companyinfo__tag')]")).getText().replaceAll("\n", " ");
            String recruiter = j.findElement(By.xpath(".//div[contains(@class, 'companyinfo__staff-name')]")).getText().trim();
            String href = j.findElement(By.xpath(".//a[@class='joblist-box__iteminfo']")).getAttribute("href");
            Job job = new Job();
            job.setHref(href);
            job.setJobName(jobName);
            job.setJobArea(jobArea);
            job.setSalary(salary);
            job.setRecruiter(recruiter);
            job.setCompanyTag(companyTag);
            job.setCompanyName(companyName);
            result.add(job);
        });
        return result;
    }

    private static void login() {
        CHROME_DRIVER.get(loginUrl);
        if (SeleniumUtil.isCookieValid("./src/main/java/zhilian/cookie.json")) {
            SeleniumUtil.loadCookie("./src/main/java/zhilian/cookie.json");
            CHROME_DRIVER.navigate().refresh();
            SeleniumUtil.sleep(1);
        }
        if (isLoginRequired()) {
            scanLogin();
        }
    }

    private static void scanLogin() {
        try {
            WebElement button = CHROME_DRIVER.findElement(By.xpath("//div[@class='zppp-panel-normal-bar__img']"));
            button.click();
            log.info("等待扫码登录中...");
            WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@class='zp-main__personal']")));
            log.info("扫码登录成功！");
            SeleniumUtil.saveCookie("./src/main/java/zhilian/cookie.json");
        } catch (Exception e) {
            log.error("扫码登录异常！");
            System.exit(-1);
        }
    }

    private static boolean isLoginRequired() {
        return !CHROME_DRIVER.getCurrentUrl().contains("i.zhaopin.com");
    }
}
