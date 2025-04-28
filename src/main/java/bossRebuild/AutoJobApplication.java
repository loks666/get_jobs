package bossRebuild;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class AutoJobApplication {

    private WebDriver driver;
    private List<String> jobLinks = new ArrayList<>();  // 存储职位的链接，避免重复
    private static final String JOB_PAGE_URL = "https://www.zhipin.com/web/geek/jobs?city=101130100&salary=406&query=java%E6%B5%8B%E8%AF%95%E5%B7%A5%E7%A8%8B%E5%B8%88%E5%AE%9E%E4%B9%A0";  // 替换为实际的招聘页面URL
    private static final String LOGIN_PAGE_URL = "https://www.zhipin.com/web/user/login"; // 登录页面的 URL
    private static final String COOKIE_FILE_PATH = "src/main/resources/cookie.json";  // 文件路径为 src/main/resources

    private static final String USERNAME = "your-username";  // 填写你的用户名
    private static final String PASSWORD = "your-password";  // 填写你的密码

    public AutoJobApplication() {
        // 使用 WebDriverManager 来自动管理 ChromeDriver 的版本
        WebDriverManager.chromedriver().setup();

        // 设置 ChromeOptions，防止浏览器显示界面
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-extensions");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        driver = new ChromeDriver(options);
    }

    // 打开网页并加载 Cookies
    public void openPage() {
        driver.get(LOGIN_PAGE_URL);

        // 执行登录
        login();

        // 获取并设置 Cookies
        loadCookies();

        // 打开职位页面
        driver.get(JOB_PAGE_URL);

        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);  // 设置隐式等待时间
    }

    // 执行登录操作
    private void login() {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30)); // 设置等待时间为 30 秒

            // 输入用户名
            WebElement usernameField = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("account")));
            usernameField.sendKeys(USERNAME);

            // 输入密码
            WebElement passwordField = driver.findElement(By.id("password"));
            passwordField.sendKeys(PASSWORD);

            // 点击登录按钮
            WebElement loginButton = driver.findElement(By.xpath("//button[text()='登录']"));
            loginButton.click();

            // 等待页面加载完成
            wait.until(ExpectedConditions.urlContains("https://www.zhipin.com/web/"));

            System.out.println("登录成功！");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 读取 Cookie 文件并设置到浏览器
    private void loadCookies() {
        try {
            // 获取所有 Cookies
            Set<Cookie> cookies = driver.manage().getCookies();  // 获取 Set 类型的 Cookies

            // 转换为 List 类型
            List<Cookie> cookieList = new ArrayList<>(cookies);

            // 保存 Cookies 到文件
            saveCookies(cookieList);

            // 从文件中读取 Cookie 信息
            JsonArray cookiesJsonArray = new Gson().fromJson(new FileReader(COOKIE_FILE_PATH), JsonArray.class);

            for (int i = 0; i < cookiesJsonArray.size(); i++) {
                JsonObject cookieObject = cookiesJsonArray.get(i).getAsJsonObject();

                // 获取 Cookie 信息
                String name = cookieObject.get("name").getAsString();
                String value = cookieObject.get("value").getAsString();
                String domain = cookieObject.get("domain").getAsString();
                String path = cookieObject.get("path").getAsString();
                boolean isSecure = cookieObject.get("isSecure").getAsBoolean();
                boolean isHttpOnly = cookieObject.get("isHttpOnly").getAsBoolean();

                // 创建 Cookie 对象并添加到浏览器中
                Cookie cookie = new Cookie(name, value, domain, path, null, isSecure, isHttpOnly);
                driver.manage().addCookie(cookie);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 保存 Cookies 到文件
    private void saveCookies(List<Cookie> cookies) {
        try {
            // 转换为 JSON 格式并保存到文件
            JsonArray cookiesJsonArray = new JsonArray();
            for (Cookie cookie : cookies) {
                JsonObject cookieObject = new JsonObject();
                cookieObject.addProperty("name", cookie.getName());
                cookieObject.addProperty("value", cookie.getValue());
                cookieObject.addProperty("domain", cookie.getDomain());
                cookieObject.addProperty("path", cookie.getPath());
                cookieObject.addProperty("isSecure", cookie.isSecure());
                cookieObject.addProperty("isHttpOnly", cookie.isHttpOnly());

                cookiesJsonArray.add(cookieObject);
            }

            // 保存到文件
            try (FileWriter writer = new FileWriter(COOKIE_FILE_PATH)) {
                writer.write(cookiesJsonArray.toString());
                System.out.println("Cookies 已保存到文件。");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 滚动页面，直到所有职位加载完成
    public void collectJobs() {
        int previousJobCount = 0;
        int jobIndex = 1;

        while (jobLinks.size() < 15) {  // 设置收集的职位数为15个
            // 构造 XPath，定位到新的职位元素
            String jobXpath = "//*[@id='wrap']/div[2]/div[3]/div/div/div[1]/ul/div[" + jobIndex + "]";
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            try {
                List<WebElement> jobElements = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath(jobXpath)));  // 显式等待

                // 输出当前页面收集的职位数量
                System.out.println("收集到的职位数量: " + jobLinks.size());

                // 收集新加载的职位信息
                for (WebElement jobElement : jobElements) {
                    String jobLink = jobElement.findElement(By.tagName("a")).getAttribute("href");

                    // 判断职位是否已经收集过
                    if (!jobLinks.contains(jobLink)) {
                        jobLinks.add(jobLink);
                        System.out.println("收集到新职位: " + jobLink);
                    }
                }

                // 判断是否收集到足够的职位
                if (jobLinks.size() >= 15) {
                    break;  // 如果收集到15个职位，退出循环
                }

                // 滚动页面左侧的职位卡片列表以加载更多职位
                scrollLeftFrame();
                jobIndex++;

            } catch (TimeoutException e) {
                System.out.println("Timeout waiting for positions to load.");
                break;  // 如果超时，退出循环
            }
        }

        System.out.println("最终收集到的职位数量: " + jobLinks.size());
    }

    // 滚动左侧的职位卡片列表以加载更多职位
    private void scrollLeftFrame() {
        // 定位到职位列表容器的滚动框
        WebElement jobListContainer = driver.findElement(By.xpath("//div[@class='job-list-container']"));

        // 使用 JavaScript 滚动该元素，模拟用户向下滚动
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollTop = arguments[0].scrollHeight", jobListContainer);

        try {
            Thread.sleep(1500);  // 等待页面加载更多职位
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // 关闭浏览器
    public void close() {
        driver.quit();
    }

    public static void main(String[] args) {
        AutoJobApplication autoJobApplication = new AutoJobApplication();

        try {
            autoJobApplication.openPage();
            autoJobApplication.collectJobs();  // 收集15个职位
        } finally {
            autoJobApplication.close();
        }
    }
}
