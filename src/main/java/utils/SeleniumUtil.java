package utils;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;


import static utils.Constant.*;

@Slf4j
public class SeleniumUtil {
    public static void initDriver() {
        SeleniumUtil.getChromeDriver();
        SeleniumUtil.getActions();
        SeleniumUtil.getWait(1500);
    }

    public static void getChromeDriver() {
        ChromeOptions options = new ChromeOptions();
        options.setBinary("C:/Program Files/Google/Chrome/Application/chrome.exe");
        System.setProperty("webdriver.chrome.driver", "src/main/resources/chromedriver.exe");
        options.addArguments("--window-position=2600,750"); // 将窗口移动到副屏的起始位置
        options.addArguments("--window-size=1600,1000"); // 设置窗口大小以适应副屏分辨率
        options.addArguments("--start-maximized"); // 最大化窗口
        CHROME_DRIVER = new ChromeDriver(options);
    }

    public static void getActions() {
        ACTIONS = new Actions(Constant.CHROME_DRIVER);
    }

    public static void getWait(long time) {
        WAIT = new WebDriverWait(Constant.CHROME_DRIVER, time);
    }

}
