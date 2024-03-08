package utils;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.Optional;

import static utils.Constant.WAIT;

@Slf4j
public class SeleniumUtil {
    public static void initDriver() {
        Constant.CHROME_DRIVER = SeleniumUtil.getChromeDriver();
        Constant.ACTIONS = SeleniumUtil.getActions(Constant.CHROME_DRIVER);
        Constant.WAIT = SeleniumUtil.getWait(Constant.CHROME_DRIVER, 1500);
    }

    public static ChromeDriver getChromeDriver() {
        ChromeOptions options = new ChromeOptions();
        options.setBinary("C:/Program Files/Google/Chrome/Application/chrome.exe");
        System.setProperty("webdriver.chrome.driver", "src/main/resources/chromedriver.exe");
        options.addArguments("--window-position=2600,750"); // 将窗口移动到副屏的起始位置
        options.addArguments("--window-size=1600,1000"); // 设置窗口大小以适应副屏分辨率
        options.addArguments("--start-maximized"); // 最大化窗口
        return new ChromeDriver(options);
    }

    public static Actions getActions(ChromeDriver driver) {
        return new Actions(driver);
    }

    public static WebDriverWait getWait(ChromeDriver driver, long time) {
        return new WebDriverWait(driver, time);
    }

}
