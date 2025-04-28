package utils;

import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static utils.Constant.ACTIONS;

/**
 * @author Summer
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */

public class RandomUserBehaviorSimulator {
    private static final ChromeDriver driver = Constant.CHROME_DRIVER;
    private static final Random random = new Random();
    private static final Logger log = LoggerFactory.getLogger(RandomUserBehaviorSimulator.class);
    private static final String[] randomTexts = {
            "您好，我对这个岗位很感兴趣！",
            "请问可以详细介绍一下岗位职责吗？",
            "工作地点在哪里？",
            "这个职位的发展前景如何？",
            "我的简历已经投递，请查收。",
            "能否提供一下具体的薪资范围？",
            "我有相关的工作经验，非常符合要求。",
            "期待与贵公司合作。",
            "这是一次很棒的求职机会！",
            "岗位要求中的技能我都具备。",
            "请问这个职位还在招聘吗？",
            "非常期待能够加入贵公司。",
            "我对贵公司的文化非常认可。",
            "请问这个岗位是否支持远程办公？",
            "这是我梦寐以求的工作！"
    };
    /**
     * 模拟随机的用户行为，确保不干扰页面核心自动化行为
     */
    public static void simulateRandomUserBehavior() {
        // 模拟随机滚动页面
        simulateRandomScrolling();

        // 模拟随机输入文本
        simulateRandomInput();

        // 随机等待时间
        sleepRandom(0, 2);
    }

    /**
     * 随机模拟页面滚动
     */
    private static void simulateRandomScrolling() {
        int numScrolls = random.nextInt(3) + 1; // 随机滚动次数 1 到 3 次

        for (int i = 0; i < numScrolls; i++) {
            // 增加滚动幅度，范围为 -500 到 +500 像素
            int scrollAmount = random.nextInt(1001) - 500; // 随机滚动 -500 到 +500 像素
            String script = String.format("window.scrollBy(0, %d);", scrollAmount);
            driver.executeScript(script);

            try {
                TimeUnit.MILLISECONDS.sleep(random.nextInt(300) + 100); // 随机延迟 100 到 400 毫秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Sleep was interrupted", e);
            }
        }
    }


    /**
     * 随机输入文本
     */
    private static void simulateRandomInput() {
        // 定义所有的 CSS 选择器
        List<String> selectors = List.of(
                "div.job-search-wrapper .input-wrap input",
                "div.satisfaction-feedback-wrapper textarea",
                "div.nav-search .ipt-search",
                "div.has-header .boss-search-top .boss-search-container .boss-search-input"
        );

        // 用于存储找到的 WebElement
        List<WebElement> inputs = new ArrayList<>();

        // 遍历每个选择器，查找元素
        for (String selector : selectors) {
            try {
                WebElement element = driver.findElementByCssSelector(selector);
                if (element != null) {
                    inputs.add(element);
                }
            } catch (Exception ignored) {
                // 忽略找不到元素的异常
            }
        }

        // 如果没有找到任何元素，返回
        if (inputs.isEmpty()) {
            return;
        }

        // 从找到的元素中随机选择一个
        WebElement selectedInput = inputs.get(random.nextInt(inputs.size()));

        // 随机选择一个文本
        String textToInput = randomTexts[random.nextInt(randomTexts.length)];

        // 对选中的元素进行逐字节输入
        selectedInput.clear();
        for (char c1 : textToInput.toCharArray()) {
            selectedInput.sendKeys(String.valueOf(c1));
            try {
                // 模拟逐字输入，间隔随机 50-200 毫秒
                TimeUnit.MILLISECONDS.sleep(random.nextInt(150) + 50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Sleep was interrupted", e);
            }
        }

        // 逐字节删除
        for (char c2 : textToInput.toCharArray()) {
            selectedInput.sendKeys(Keys.BACK_SPACE);
            try {
                // 模拟逐字输入，间隔随机 50-200 毫秒
                TimeUnit.MILLISECONDS.sleep(random.nextInt(150) + 50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Sleep was interrupted", e);
            }
        }

        // 使用 JavaScript 滚动到页面顶部
        driver.executeScript("window.scrollTo(0, 0);");

        // 等待随机的时间
        sleepRandom(1, 1);
    }


    /**
     * 模拟随机的等待时间
     */
    private static void sleepRandom(int minSeconds, int maxSeconds) {
        try {
            // 生成一个随机等待时间，范围是 minSeconds 到 maxSeconds 之间，包括 0
            int randomSleepTime = random.nextInt((maxSeconds - minSeconds) + 1) + minSeconds;
            if (randomSleepTime > 0) {
                TimeUnit.SECONDS.sleep(randomSleepTime);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Sleep was interrupted", e);
        }
    }
}