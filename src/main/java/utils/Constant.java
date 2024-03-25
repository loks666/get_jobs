package utils;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class Constant {
    public static ChromeDriver CHROME_DRIVER;
    public static Actions ACTIONS;
    public static WebDriverWait WAIT;
    public static int WAIT_TIME = 20;
    public static String SAY_HI = "您好，7年工作经验，有AIGC大模型、Java，Python，Golang和运维的相关经验，希望应聘这个岗位，期待可以与您进一步沟通，谢谢！";
}
