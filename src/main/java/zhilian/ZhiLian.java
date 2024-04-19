package zhilian;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Job;
import utils.JobUtils;
import utils.SeleniumUtil;

import java.util.ArrayList;
import java.util.List;

import static utils.Constant.*;

public class ZhiLian {
    private static final Logger log = LoggerFactory.getLogger(ZhiLian.class);

    static String loginUrl = "https://passport.zhaopin.com/login";

    static String homeUrl = "https://sou.zhaopin.com/?";

    static boolean isLimit = false;

    static int maxPage = 500;

    static ZhilianConfig config = ZhilianConfig.init();


    public static void main(String[] args) {
        SeleniumUtil.initDriver();
        login();
        config.getKeywords().forEach(keyword -> {
            CHROME_DRIVER.get(getSearchUrl(keyword, 1));
            submitJobs(keyword);
            isLimit = false;
        });
        CHROME_DRIVER.quit();
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
        WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(@class, 'joblist-box__item')]")));
        setMaxPages();
        for (int i = 1; i <= maxPage; i++) {
            if (i != 1) {
                CHROME_DRIVER.get(getSearchUrl(keyword, i));
            }
            log.info("开始投递【{}】关键词，第【{}】页...", keyword, i);
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
                continue;
            }
            // 投递
            WebElement submit = WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@class='a-job-apply-button']")));
            submit.click();
            if (checkIsLimit()){
                break;
            }
            SeleniumUtil.sleep(1);
            // 切换到新的标签页
            ArrayList<String> tabs = new ArrayList<>(CHROME_DRIVER.getWindowHandles());
            CHROME_DRIVER.switchTo().window(tabs.get(tabs.size() - 1));
            //关闭弹框
            try {
                WebElement result = CHROME_DRIVER.findElement(By.xpath("//div[@class='deliver-dialog']"));
                if (result.getText().contains("申请成功")) {
                    log.info("岗位申请成功！");
                }
            } catch (Exception e) {
                log.error("关闭投递弹框失败...");
            }
            try {
                WebElement close = CHROME_DRIVER.findElement(By.xpath("//img[@title='close-icon']"));
                close.click();
            } catch (Exception e) {
                if (checkIsLimit()) {
                    break;
                }
            }
            try {
                // 投递相似职位
                WebElement checkButton = CHROME_DRIVER.findElement(By.xpath("//div[contains(@class, 'applied-select-all')]//input"));
                if (!checkButton.isSelected()) {
                    checkButton.click();
                }
                List<WebElement> jobs = CHROME_DRIVER.findElements(By.xpath("//div[@class='recommend-job']"));
                WebElement post = CHROME_DRIVER.findElement(By.xpath("//div[contains(@class, 'applied-select-all')]//button"));
                post.click();
                printRecommendJobs(jobs);
                log.info("相似职位投递成功！");
            } catch (NoSuchElementException e) {
                log.error("没有匹配到相似职位...");
            } catch (Exception e) {
                log.error("相似职位投递异常！！！");
            }
            // 投完了关闭当前窗口并切换至第一个窗口
            CHROME_DRIVER.close();
            CHROME_DRIVER.switchTo().window(tabs.get(0));
        }
    }

    private static boolean checkIsLimit() {
        try {
            SeleniumUtil.sleepByMilliSeconds(500);
            WebElement result = CHROME_DRIVER.findElement(By.xpath("//div[@class='a-job-apply-workflow']"));
            if (result.getText().contains("达到上限")) {
                log.info("今日投递已达上限！");
                isLimit = true;
                return true;
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static void setMaxPages() {
        // 模拟 Ctrl + End
        ACTIONS.keyDown(Keys.CONTROL).sendKeys(Keys.END).keyUp(Keys.CONTROL).perform();
        while (true) {
            try {
                WebElement button = CHROME_DRIVER.findElement(By.xpath("//button[contains(@class, 'btn') and contains(@class, 'soupager__btn')][last()]"));
                if (button.getAttribute("disabled") != null) {
                    // 按钮被禁用，退出循环
                    break;
                }
                button.click();
            } catch (Exception ignore) {
            }
        }
        WebElement lastPage = CHROME_DRIVER.findElement(By.xpath("//span[@class='soupager__index soupager__index--active']"));
        if (lastPage != null && lastPage.getText().matches("\\d+")) {
            maxPage = Integer.parseInt(lastPage.getText());
        }
        // 模拟 Ctrl + Home
        ACTIONS.keyDown(Keys.CONTROL).sendKeys(Keys.HOME).keyUp(Keys.CONTROL).perform();
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
        });
    }

    private static List<Job> getPositionList() {
        int jobSize = CHROME_DRIVER.findElements(By.xpath("//div[@class='joblist-box__item clearfix']")).size();
        List<WebElement> jobNameList = CHROME_DRIVER.findElements(By.xpath("//div[@class='jobinfo__top']//a"));
        List<WebElement> salaryList = CHROME_DRIVER.findElements(By.xpath("//div[@class='jobinfo__top']//p"));
        List<WebElement> jobAreaList = CHROME_DRIVER.findElements(By.xpath("//div[@class='jobinfo__other-info-item']//span"));
        List<WebElement> companyNameList = CHROME_DRIVER.findElements(By.xpath("//div[contains(@class, 'companyinfo__top')]//a"));
        List<WebElement> companyTagList = CHROME_DRIVER.findElements(By.xpath("//div[contains(@class, 'companyinfo__tag')]"));
        List<WebElement> recruiterList = CHROME_DRIVER.findElements(By.xpath("//div[contains(@class, 'companyinfo__staff-name')]"));
        if (jobNameList.size() != jobSize) {
            log.info("jobNameList size does not match jobSize");
        }
        if (salaryList.size() != jobSize) {
            log.info("salaryList size does not match jobSize");
        }
        if (jobAreaList.size() != jobSize) {
            log.info("jobAreaList size does not match jobSize");
        }
        if (companyNameList.size() != jobSize) {
            log.info("companyNameList size does not match jobSize");
        }
        if (companyTagList.size() != jobSize) {
            log.info("companyTagList size does not match jobSize");
        }
        if (recruiterList.size() != jobSize) {
            log.info("recruiterList size does not match jobSize");
        }
        ArrayList<Job> result = new ArrayList<>();
        for (int i = 0; i < jobSize; i++) {
            Job job = new Job();
            job.setJobName(jobNameList.get(i).getText());
            job.setSalary(salaryList.get(i).getText());
            job.setJobArea(jobAreaList.get(i).getText().replaceAll("\n", " "));
            job.setRecruiter(recruiterList.get(i).getText().trim());
            job.setCompanyTag(companyTagList.get(i).getText().replaceAll("\n", " "));
            job.setHref(companyNameList.get(i).getAttribute("href"));
            job.setCompanyName(companyNameList.get(i).getText());
            result.add(job);
            log.info("选中【{}】公司【{}】岗位，【{}】地区，薪资【{}】，标签【{}】，HR【{}】", job.getCompanyName(), job.getJobName(), job.getJobArea(), job.getSalary(), job.getCompanyTag(), job.getRecruiter());
        }
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
