package utils;

import boss.BossConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v135.network.Network;
import org.openqa.selenium.devtools.v135.network.model.Headers;
import org.openqa.selenium.devtools.v135.page.Page;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static utils.Constant.*;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
public class SeleniumUtil {
    private static final Logger log = LoggerFactory.getLogger(SeleniumUtil.class);

    public static void initDriver(boolean mobile) {
        SeleniumUtil.getChromeDriver(mobile);
        SeleniumUtil.getActions();
        SeleniumUtil.getWait(WAIT_TIME);
    }

    public static void initDriver() {
        SeleniumUtil.getChromeDriver();
        SeleniumUtil.getActions();
        SeleniumUtil.getWait(WAIT_TIME);
    }

    public static void getChromeDriver(){
        getChromeDriver(false);
    }

    public static void getChromeDriver(Boolean mobile) {
        ChromeOptions options = new ChromeOptions();
        // 添加扩展插件
        String osName = System.getProperty("os.name").toLowerCase();
        KeyUtil.printLog();
        log.info("当前操作系统为【{}】", osName);
        String osType = getOSType(osName);
        switch (osType) {
            case "windows":
                options.setBinary("C:/Program Files/Google/Chrome/Application/chrome.exe");//TODO 注意: 这里需要修改为你的chrome的安装路径,不然启动会报错!!! 右键chrome图标右键，选择属性，复制路径
                System.setProperty("webdriver.chrome.driver", ProjectRootResolver.rootPath+"/src/main/resources/chromedriver.exe");
                break;
            case "mac":
                options.setBinary("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
                System.setProperty("webdriver.chrome.driver", ProjectRootResolver.rootPath+"/src/main/resources/chromedriver");
                break;
            case "linux":
                options.setBinary("/usr/bin/google-chrome-stable");
                System.setProperty("webdriver.chrome.driver", ProjectRootResolver.rootPath+"/src/main/resources/chromedriver-linux64/chromedriver");
                break;
            default:
                log.info("你这什么破系统，没见过，别跑了!");
                break;
        }
        BossConfig config = BossConfig.init();
        if (config.getDebugger()) {
            options.addExtensions(new File(ProjectRootResolver.rootPath+"/src/main/resources/xpathHelper.crx"));
        } else {
            options.addArguments("--disable-extensions");
        }
        GraphicsDevice[] screens = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        if (screens.length > 1) {
            options.addArguments("--window-position=2800,1000"); //将窗口移动到副屏的起始位置
        }
//        options.addArguments("--headless"); //使用无头模式

        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false); // 禁用默认扩展
        options.addArguments("user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36");




        CHROME_DRIVER = new ChromeDriver(options);
        CHROME_DRIVER.manage().window().maximize();


        // 创建移动设备Chrome驱动
        ChromeOptions mobileOptions = new ChromeOptions();
        addMobileEmulationOptions(mobileOptions);

        if(mobile){
            MOBILE_CHROME_DRIVER = new ChromeDriver(mobileOptions);
            MOBILE_CHROME_DRIVER.manage().window().maximize();
        }

    }

    /**
     * 添加移动设备模拟配置到ChromeOptions
     * @param options ChromeOptions对象
     */
    private static void addMobileEmulationOptions(ChromeOptions options) {
        // 添加移动设备模拟配置
        Map<String, Object> mobileEmulation = new HashMap<>();
        mobileEmulation.put("deviceName", "iPhone X");
        // 如果需要自定义设备参数，可以使用下面的配置替代deviceName
        // Map<String, Object> deviceMetrics = new HashMap<>();
        // deviceMetrics.put("width", 375);
        // deviceMetrics.put("height", 812);
        // deviceMetrics.put("pixelRatio", 3.0);
        // mobileEmulation.put("deviceMetrics", deviceMetrics);
        // mobileEmulation.put("userAgent", "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1");

        options.setExperimentalOption("mobileEmulation", mobileEmulation);

        options.addArguments("--disable-features=ExternalProtocolDialog"); // 禁用弹窗（部分版本有效）
    }

    private static String getOSType(String osName) {
        if (osName.contains("win")) {
            return "windows";
        }
        if (osName.contains("linux")) {
            return "linux";
        }
        if (osName.contains("mac") || osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            return "mac";
        }
        return "unknown";
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
        saveCookieToFile(jsonArray, path);
    }

    private static void saveCookieToFile(JSONArray jsonArray, String path) {
        // 将JSONArray写入到一个文件中
        try (FileWriter file = new FileWriter(path)) {
            file.write(jsonArray.toString(4));  // 使用4个空格的缩进
            log.info("Cookie已保存到文件：{}", path);
        } catch (IOException e) {
            log.error("保存cookie异常！保存路径:{}", path);
        }
    }

    private static void updateCookieFile(JSONArray jsonArray, String path) {
        // 将JSONArray写入到一个文件中
        try (FileWriter file = new FileWriter(path)) {
            file.write(jsonArray.toString(4));  // 使用4个空格的缩进
            log.info("cookie文件更新：{}", path);
        } catch (IOException e) {
            log.error("更新cookie异常！保存路径:{}", path);
        }
    }

    public static void loadCookie(String cookiePath) {
        // 首先清除由于浏览器打开已有的cookies
        CHROME_DRIVER.manage().deleteAllCookies();
        if(Objects.nonNull(MOBILE_CHROME_DRIVER)){
            MOBILE_CHROME_DRIVER.manage().deleteAllCookies();
        }
        // 从文件中读取JSONArray
        JSONArray jsonArray = null;
        try {
            String jsonText = new String(Files.readAllBytes(Paths.get(cookiePath)));
            if (!jsonText.isEmpty()) {
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
                    expiry = new Date(Instant.now().plus(7, ChronoUnit.DAYS).toEpochMilli());
                    jsonObject.put("expiry", Instant.now().plus(7, ChronoUnit.DAYS).toEpochMilli()); // 更新expiry
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
                    if(Objects.nonNull(MOBILE_CHROME_DRIVER)){
                        MOBILE_CHROME_DRIVER.manage().addCookie(cookie);
                    }
                } catch (Exception ignore) {
                }
            }
            // 更新cookie文件
            updateCookieFile(jsonArray, cookiePath);
        }
    }

    public static void getActions() {
        ACTIONS = new Actions(Constant.CHROME_DRIVER);
        if(Objects.nonNull(MOBILE_CHROME_DRIVER)){
            MOBILE_ACTIONS = new Actions(MOBILE_CHROME_DRIVER);
        }
    }

    public static void getWait(long time) {
        WAIT = new WebDriverWait(Constant.CHROME_DRIVER, Duration.ofSeconds(time));
        if(Objects.nonNull(MOBILE_CHROME_DRIVER)){
            MOBILE_WAIT = new WebDriverWait(MOBILE_CHROME_DRIVER,Duration.ofSeconds(time));
        }
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

    public static Optional<WebElement> findElement(String xpath, String message) {
        try {
            return Optional.of(CHROME_DRIVER.findElement(By.xpath(xpath)));
        } catch (Exception e) {
            log.error(message);
            return Optional.empty();
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

    public static void simulateRandomUserBehavior(boolean condition){
        if(condition){
            RandomUserBehaviorSimulator.simulateRandomUserBehavior();
        }
    }


    /**
     * 注入反自动化检测的脚本，隐藏 webdriver、语言、插件等特征字段。
     * 必须在 driver.get(url) 之前调用。
     */
    public static void injectStealthJs(DevTools devTools) {
        String script = """
            Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
            delete cdc_adoQpoasnfa76pfcZLmcfl_Array;
            delete cdc_adoQpoasnfa76pfcZLmcfl_JSON;
            delete cdc_adoQpoasnfa76pfcZLmcfl_Object;
            delete cdc_adoQpoasnfa76pfcZLmcfl_Promise;
            delete cdc_adoQpoasnfa76pfcZLmcfl_Proxy;
            delete cdc_adoQpoasnfa76pfcZLmcfl_Symbol;
            delete cdc_adoQpoasnfa76pfcZLmcfl_Window;
            window.navigator.chrome = { runtime: {} };
            Object.defineProperty(navigator, 'languages', {get: () => ['zh-CN', 'zh']});
            Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3]});
        """;

        devTools.send(Page.addScriptToEvaluateOnNewDocument(
                script,
                Optional.empty(),
                Optional.of(false),
                Optional.of(true)
        ));
    }

    /**
     * 设置标准防指纹伪装请求头（适配 Chrome 135，macOS 平台）
     */
    public static void setDefaultHeaders(DevTools devTools) {
        Map<String, Object> headersMap = new HashMap<>();
//        headersMap.put("sec-ch-ua", "\"Google Chrome\";v=\"135\", \"Chromium\";v=\"135\", \"Not;A=Brand\";v=\"99\"");
        headersMap.put("sec-ch-ua", "\"Google Chrome\";v=\"135\", \"Not-A.Brand\";v=\"8\", \"Chromium\";v=\"135\"");
        headersMap.put("sec-ch-ua-mobile", "?0");
        headersMap.put("sec-ch-ua-platform", "\"macOS\"");
        headersMap.put("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36");
        headersMap.put("accept-language", "zh-CN,zh;q=0.9");
        headersMap.put("referer", "https://www.zhipin.com/");

        devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
        devTools.send(Network.setExtraHTTPHeaders(new Headers(headersMap)));
    }




}
