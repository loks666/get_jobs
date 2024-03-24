package utils;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static utils.Constant.*;

@Slf4j
public class SeleniumUtil {
    public static void initDriver() {
        SeleniumUtil.getChromeDriver();
        SeleniumUtil.getActions();
        SeleniumUtil.getWait(WAIT_TIME);
    }
    public static void getChromeDriver() {
        ChromeOptions options = new ChromeOptions();
        // 添加扩展插件
        options.setBinary("C:/Program Files/Google/Chrome/Application/chrome.exe");
        options.addExtensions(new File("src/main/resources/xpathHelper.crx"));
        System.setProperty("webdriver.chrome.driver", "src/main/resources/chromedriver.exe");
        options.addArguments("--window-position=2600,750"); //将窗口移动到副屏的起始位置
        options.addArguments("--window-size=1600,1000"); //设置窗口大小以适应副屏分辨率
        options.addArguments("--start-maximized"); //最大化窗口
//        options.addArguments("--headless"); //使用无头模式
        CHROME_DRIVER = new ChromeDriver(options);
    }

    public static void saveCookie(String path) {
        // 获取所有的cookies
        Set<Cookie> cookies = CHROME_DRIVER.manage().getCookies();
        // 创建一个JSONArray来保存所有的cookie信息
        JSONArray jsonArray = new JSONArray();
        // 将每个cookie转换为一个JSONObject，并添加到JSONArray中
        for (Cookie cookie : cookies) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("name", cookie.getName());
            jsonObject.put("value", cookie.getValue());
            jsonObject.put("domain", cookie.getDomain());
            jsonObject.put("path", cookie.getPath());
            if (cookie.getExpiry() != null) {
                jsonObject.put("expiry", cookie.getExpiry().getTime());
            }
            jsonObject.put("isSecure", cookie.isSecure());
            jsonObject.put("isHttpOnly", cookie.isHttpOnly());
            jsonArray.put(jsonObject);
        }
        // 将JSONArray写入到一个文件中
        try (FileWriter file = new FileWriter(path)) {
            file.write(jsonArray.toString(4));  // 使用4个空格的缩进
            log.info("Cookie已保存到文件：{}", path);
        } catch (IOException e) {
            log.error("保存cookie异常！保存路径:{}", path);
        }
    }

    public static void loadCookie(String cookiePath) {
        // 首先清除由于浏览器打开已有的cookies
        CHROME_DRIVER.manage().deleteAllCookies();

        // 从文件中读取JSONArray
        JSONArray jsonArray = null;
        try {
            String jsonText = new String(Files.readAllBytes(Paths.get(cookiePath)));
            if (!jsonText.isBlank()){
                jsonArray = new JSONArray(jsonText);
            }
        } catch (IOException e) {
            log.error("读取cookie异常！");
        }
        // 遍历JSONArray中的每个JSONObject，并从中获取cookie的信息
        if (jsonArray != null) {
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String name = jsonObject.getString("name");
                String value = jsonObject.getString("value");
                String domain = jsonObject.getString("domain");
                String path = jsonObject.getString("path");
                Date expiry = null;
                if (!jsonObject.isNull("expiry")) {
                    expiry = new Date(jsonObject.getLong("expiry"));
                }
                boolean isSecure = jsonObject.getBoolean("isSecure");
                boolean isHttpOnly = jsonObject.getBoolean("isHttpOnly");

                // 使用这些信息来创建新的Cookie对象，并将它们添加到WebDriver中
                Cookie cookie = new Cookie.Builder(name, value)
                        .domain(domain)
                        .path(path)
                        .expiresOn(expiry)
                        .isSecure(isSecure)
                        .isHttpOnly(isHttpOnly)
                        .build();
                try {
                    CHROME_DRIVER.manage().addCookie(cookie);
                } catch (Exception e) {
                    log.error("【小问题无须担心】cookie添加异常:【{}】", cookie);
                }
            }
        }
    }

    public static void getActions() {
        ACTIONS = new Actions(Constant.CHROME_DRIVER);
    }

    public static void getWait(long time) {
        WAIT = new WebDriverWait(Constant.CHROME_DRIVER, time);
    }

    public static void sleep(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Sleep was interrupted", e);
        }
    }

    public static void sleepByMilliSeconds(int milliSeconds) {
        try {
            TimeUnit.MILLISECONDS.sleep(milliSeconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Sleep was interrupted", e);
        }
    }

    public static List<WebElement> findElements(By by) {
        try {
            return CHROME_DRIVER.findElements(by);
        } catch (Exception e) {
            log.error("Could not find element:{}", by, e);
            return new ArrayList<>();
        }
    }

    public static WebElement findElement(By by) {
        try {
            return CHROME_DRIVER.findElement(by);
        } catch (Exception e) {
            log.error("Could not find element:{}", by, e);
            return null;
        }
    }

    public static void click(By by) {
        try {
            CHROME_DRIVER.findElement(by).click();
        } catch (Exception e) {
            log.error("click element:{}", by, e);
        }
    }

    public static boolean isCookieValid(String cookiePath) {
        return Files.exists(Paths.get(cookiePath));
    }
}
