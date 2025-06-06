package boss;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JobUtils;
import utils.PlaywrightUtil;
import utils.SeleniumUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static utils.Constant.ACTIONS;
import static utils.Constant.CHROME_DRIVER;

/**
 * Boss直聘页面操作工具类
 * 封装常见页面操作逻辑
 * 
 * 替代原始代码中重复的页面操作逻辑:
 * - 下拉加载更多岗位
 * - 随机等待和模拟用户行为
 * - 标签页管理
 * - 滚动页面
 * - 发送简历
 */
public class BossPageOperations {
    private static final Logger log = LoggerFactory.getLogger(BossPageOperations.class);
    private static final Random random = new Random();

    /**
     * 页面下拉加载更多岗位
     * 
     * 替代原始代码:
     * ```java
     * // 记录下拉前后的岗位数量
     * int previousJobCount = 0;
     * int currentJobCount = 0;
     * int unchangedCount = 0;
     * 
     * while (unchangedCount < 2) {
     * // 获取所有岗位卡片
     * List<ElementHandle> jobCards = page.querySelectorAll("ul.rec-job-list
     * li.job-card-box");
     * currentJobCount = jobCards.size();
     * 
     * if (currentJobCount > previousJobCount) {
     * previousJobCount = currentJobCount;
     * unchangedCount = 0;
     * PlaywrightUtil.evaluate("window.scrollTo(0, document.body.scrollHeight)");
     * page.waitForTimeout(2000);
     * } else {
     * unchangedCount++;
     * }
     * }
     * ```
     * 
     * @param page            Playwright页面对象
     * @param maxLoadAttempts 最大尝试加载次数
     * @return 最终加载的岗位数量
     */
    public static int scrollToLoadMoreJobs(Page page, int maxLoadAttempts) {
        int previousJobCount = 0;
        int currentJobCount = 0;
        int unchangedCount = 0;
        int loadAttempts = 0;

        while (unchangedCount < 2 && loadAttempts < maxLoadAttempts) {
            // 获取所有岗位卡片
            List<ElementHandle> jobCards = page.querySelectorAll(BossElementLocators.JOB_CARD_BOX);
            currentJobCount = jobCards.size();

            log.info("当前已加载岗位数量: " + currentJobCount);

            // 判断是否有新增岗位
            if (currentJobCount > previousJobCount) {
                previousJobCount = currentJobCount;
                unchangedCount = 0;

                // 滚动到页面底部加载更多
                PlaywrightUtil.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                log.info("下拉页面加载更多...");

                // 等待新内容加载
                page.waitForTimeout(2000);
            } else {
                unchangedCount++;
                if (unchangedCount < 2) {
                    log.info("下拉后岗位数量未增加，再次尝试...");
                    // 再次尝试滚动
                    page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                    page.waitForTimeout(2000);
                } else {
                    break;
                }
            }

            loadAttempts++;
        }

        log.info("已获取所有可加载岗位，共计: " + currentJobCount + " 个");
        return currentJobCount;
    }

    /**
     * 模拟随机等待时间
     * 
     * 替代原始代码:
     * ```java
     * private static void RandomWait() {
     * SeleniumUtil.sleep(JobUtils.getRandomNumberInRange(3, 20));
     * }
     * ```
     */
    public static void randomWait() {
        int seconds = JobUtils.getRandomNumberInRange(3, 20);
        SeleniumUtil.sleep(seconds);
    }

    /**
     * 模拟用户浏览行为
     * 
     * 替代原始代码:
     * ```java
     * private static void simulateWait() {
     * for (int i = 0; i < 3; i++) {
     * ACTIONS.sendKeys(" ").perform();
     * SeleniumUtil.sleep(1);
     * }
     * ACTIONS.keyDown(Keys.CONTROL)
     * .sendKeys(Keys.HOME)
     * .keyUp(Keys.CONTROL)
     * .perform();
     * SeleniumUtil.sleep(1);
     * }
     * ```
     */
    public static void simulateUserBrowsing() {
        for (int i = 0; i < 3; i++) {
            ACTIONS.sendKeys(" ").perform();
            SeleniumUtil.sleep(1);
        }
        ACTIONS.keyDown(Keys.CONTROL)
                .sendKeys(Keys.HOME)
                .keyUp(Keys.CONTROL)
                .perform();
        SeleniumUtil.sleep(1);
    }

    /**
     * 关闭当前标签页并返回到指定的标签页
     * 
     * 替代原始代码:
     * ```java
     * private static void closeWindow(ArrayList<String> tabs) {
     * SeleniumUtil.sleep(1);
     * CHROME_DRIVER.close();
     * CHROME_DRIVER.switchTo().window(tabs.get(0));
     * }
     * ```
     * 
     * @param tabs     标签页列表
     * @param tabIndex 要切换到的标签页索引
     */
    public static void closeCurrentTabAndSwitchTo(ArrayList<String> tabs, int tabIndex) {
        SeleniumUtil.sleep(1);
        CHROME_DRIVER.close();
        CHROME_DRIVER.switchTo().window(tabs.get(tabIndex));
    }

    /**
     * 在新标签页中打开链接
     * 
     * 替代原始代码:
     * ```java
     * JavascriptExecutor jse = CHROME_DRIVER;
     * jse.executeScript("var newTab = window.open(arguments[0], '_blank');
     * newTab.blur(); window.focus();", job.getHref());
     * ArrayList<String> tabs = new ArrayList<>(CHROME_DRIVER.getWindowHandles());
     * CHROME_DRIVER.switchTo().window(tabs.getLast());
     * ```
     * 
     * @param url 要打开的链接
     * @return 所有标签页的句柄列表
     */
    public static ArrayList<String> openLinkInNewTab(String url) {
        JavascriptExecutor jse = CHROME_DRIVER;
        // 使用JavaScript控制焦点，避免了每次打开新页签时浏览器窗口自动切换到前台的问题
        jse.executeScript("var newTab = window.open(arguments[0], '_blank'); newTab.blur(); window.focus();", url);
        // 获取所有标签页句柄
        ArrayList<String> tabs = new ArrayList<>(CHROME_DRIVER.getWindowHandles());
        // 切换到新标签页
        CHROME_DRIVER.switchTo().window(tabs.getLast());
        return tabs;
    }

    /**
     * 向下滚动聊天记录页面，直到加载全部内容
     * 
     * 替代原始代码:
     * ```java
     * JavascriptExecutor js = CHROME_DRIVER;
     * boolean shouldBreak = false;
     * while (!shouldBreak) {
     * try {
     * WebElement bottom =
     * CHROME_DRIVER.findElement(By.xpath("//div[@class='finished']"));
     * if ("没有更多了".equals(bottom.getText())) {
     * shouldBreak = true;
     * }
     * } catch (Exception ignore) {}
     * 
     * WebElement element;
     * try {
     * WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(text(),
     * '滚动加载更多')]")));
     * element = CHROME_DRIVER.findElement(By.xpath("//div[contains(text(),
     * '滚动加载更多')]"));
     * } catch (Exception e) {
     * break;
     * }
     * 
     * if (element != null) {
     * js.executeScript("arguments[0].scrollIntoView();", element);
     * } else {
     * js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
     * }
     * }
     * ```
     */
    public static void scrollChatListUntilFinished() {
        JavascriptExecutor js = CHROME_DRIVER;
        boolean shouldBreak = false;

        while (!shouldBreak) {
            try {
                Optional<WebElement> finishedElement = BossElementFinder.findElement(BossElementLocators.FINISHED_TEXT);
                if (finishedElement.isPresent() && "没有更多了".equals(finishedElement.get().getText())) {
                    shouldBreak = true;
                }
            } catch (Exception ignore) {
                // 未找到底部标识，继续滚动
            }

            // 尝试查找"滚动加载更多"元素
            Optional<WebElement> loadMoreElement = BossElementFinder.findElement(BossElementLocators.SCROLL_LOAD_MORE);

            if (loadMoreElement.isPresent()) {
                try {
                    js.executeScript("arguments[0].scrollIntoView();", loadMoreElement.get());
                    SeleniumUtil.sleep(1);
                } catch (Exception e) {
                    log.error("滚动到元素出错", e);
                    // 尝试滚动到页面底部
                    js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                }
            } else {
                try {
                    js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                    SeleniumUtil.sleep(1);
                } catch (Exception e) {
                    log.error("滚动到页面底部出错", e);
                    break;
                }
            }

            // 防止无限循环，给一个额外的检查
            List<WebElement> items = BossElementFinder.findElements(BossElementLocators.CHAT_LIST_ITEM);
            if (items.isEmpty()) {
                log.info("没有找到聊天记录项，停止滚动");
                break;
            }
        }
    }

    /**
     * 发送简历图片
     * 
     * 替代原始代码:
     * ```java
     * public static Boolean sendResume(String company) {
     * if (!config.getSendImgResume()) {
     * return false;
     * }
     * try {
     * URL resourceUrl = Boss.class.getResource("/resume.jpg");
     * if (resourceUrl == null) {
     * return false;
     * }
     * File imageFile = new File(resourceUrl.toURI());
     * if (!imageFile.exists()) {
     * return false;
     * }
     * WebElement fileInput =
     * CHROME_DRIVER.findElement(By.xpath("//div[@aria-label='发送图片']//input[@type='file']"));
     * fileInput.sendKeys(imageFile.getAbsolutePath());
     * return true;
     * } catch (Exception e) {
     * log.error("发送简历图片时出错：{}", e.getMessage());
     * return false;
     * }
     * }
     * ```
     * 
     * @param imagePath 简历图片路径
     * @return 是否发送成功
     */
    public static boolean sendResumeImage(String imagePath) {
        try {
            Optional<WebElement> fileInput = BossElementFinder.findElement(BossElementLocators.IMAGE_UPLOAD);
            if (fileInput.isPresent()) {
                fileInput.get().sendKeys(imagePath);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("发送简历图片时出错：{}", e.getMessage());
            return false;
        }
    }
}