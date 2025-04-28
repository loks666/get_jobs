package utils;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
public class Constant {
    public static ChromeDriver CHROME_DRIVER;
    public static Actions ACTIONS;
    public static WebDriverWait WAIT;
    public static int WAIT_TIME = 30;
    public static String UNLIMITED_CODE = "0";
}