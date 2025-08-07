package utils;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Boss直聘元素查找工具类
 * 封装Selenium和Playwright元素查找逻辑
 * 
 * 替代原来代码中直接使用的:
 * - CHROME_DRIVER.findElement(By.xpath("..."))
 * - CHROME_DRIVER.findElement(By.cssSelector("..."))
 * - WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("...")))
 * - SeleniumUtil.findElement("...", "")
 */
public class Finder {
    private static final Logger log = LoggerFactory.getLogger(Finder.class);
    private static final WebDriver driver = Constant.CHROME_DRIVER;
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    /**
     * 基于Selenium WebDriver查找单个元素
     * 
     * 替代原来的代码模式:
     * WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("...")))
     * 
     * @param selector       选择器表达式
     * @param timeoutSeconds 超时时间(秒)
     * @return 找到的元素，如果没找到返回Optional.empty()
     */
    public static Optional<WebElement> findElement(String selector, int timeoutSeconds) {
        try {
            By by = parseSelector(selector);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            return Optional.of(wait.until(ExpectedConditions.presenceOfElementLocated(by)));
        } catch (Exception e) {
            log.debug("未找到元素: {}, 原因: {}", selector, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 基于Selenium WebDriver查找单个元素，使用默认超时时间
     * 
     * 替代原来的代码模式:
     * CHROME_DRIVER.findElement(By.xpath("..."))
     * 
     * @param selector 选择器表达式
     * @return 找到的元素，如果没找到返回Optional.empty()
     */
    public static Optional<WebElement> findElement(String selector) {
        return findElement(selector, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 基于Selenium WebDriver查找多个元素
     * 
     * 替代原来的代码模式:
     * CHROME_DRIVER.findElements(By.xpath("..."))
     * 
     * @param selector 选择器表达式
     * @return 找到的元素列表，如果没找到返回空列表
     */
    public static List<WebElement> findElements(String selector) {
        try {
            By by = parseSelector(selector);
            return driver.findElements(by);
        } catch (Exception e) {
            log.debug("查找元素列表失败: {}, 原因: {}", selector, e.getMessage());
            return List.of();
        }
    }

    /**
     * 等待元素可见
     * 
     * 替代原来的代码模式:
     * WAIT.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("...")))
     * 
     * @param selector       选择器表达式
     * @param timeoutSeconds 超时时间(秒)
     * @return 找到的元素，如果没找到返回Optional.empty()
     */
    public static Optional<WebElement> waitForElementVisible(String selector, int timeoutSeconds) {
        try {
            By by = parseSelector(selector);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            return Optional.of(wait.until(ExpectedConditions.visibilityOfElementLocated(by)));
        } catch (Exception e) {
            log.debug("等待元素可见超时: {}, 原因: {}", selector, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 等待元素可见，使用默认超时时间
     * 
     * @param selector 选择器表达式
     * @return 找到的元素，如果没找到返回Optional.empty()
     */
    public static Optional<WebElement> waitForElementVisible(String selector) {
        return waitForElementVisible(selector, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 等待元素可点击
     * 
     * 替代原来的代码模式:
     * WAIT.until(ExpectedConditions.elementToBeClickable(By.xpath("...")))
     * 
     * @param selector       选择器表达式
     * @param timeoutSeconds 超时时间(秒)
     * @return 找到的元素，如果没找到返回Optional.empty()
     */
    public static Optional<WebElement> waitForElementClickable(String selector, int timeoutSeconds) {
        try {
            By by = parseSelector(selector);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
            return Optional.of(wait.until(ExpectedConditions.elementToBeClickable(by)));
        } catch (Exception e) {
            log.debug("等待元素可点击超时: {}, 原因: {}", selector, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 等待元素可点击，使用默认超时时间
     * 
     * @param selector 选择器表达式
     * @return 找到的元素，如果没找到返回Optional.empty()
     */
    public static Optional<WebElement> waitForElementClickable(String selector) {
        return waitForElementClickable(selector, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 判断元素是否存在
     * 
     * 替代原来的try-catch模式:
     * try {
     * CHROME_DRIVER.findElement(By.xpath("..."));
     * return true;
     * } catch (Exception e) {
     * return false;
     * }
     * 
     * @param selector 选择器表达式
     * @return true如果元素存在，否则false
     */
    public static boolean isElementPresent(String selector) {
        try {
            By by = parseSelector(selector);
            driver.findElement(by);
            return true;
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    /**
     * 基于Playwright查找单个元素
     * 
     * 替代原来的代码模式:
     * page.querySelector("...")
     * 
     * @param page     Playwright页面对象
     * @param selector 选择器表达式
     * @return 找到的元素，如果没找到返回null
     */
    public static ElementHandle findPlaywrightElement(Page page, String selector) {
        try {
            return page.querySelector(selector);
        } catch (Exception e) {
            log.debug("Playwright未找到元素: {}, 原因: {}", selector, e.getMessage());
            return null;
        }
    }

    /**
     * 基于Playwright查找多个元素
     * 
     * 替代原来的代码模式:
     * page.querySelectorAll("...")
     * 
     * @param page     Playwright页面对象
     * @param selector 选择器表达式
     * @return 找到的元素列表，如果没找到返回空列表
     */
    public static List<ElementHandle> findPlaywrightElements(Page page, String selector) {
        try {
            return page.querySelectorAll(selector);
        } catch (Exception e) {
            log.debug("Playwright查找元素列表失败: {}, 原因: {}", selector, e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取Playwright的Locator对象
     * 
     * 替代原来的代码模式:
     * page.locator("...")
     * 
     * @param page     Playwright页面对象
     * @param selector 选择器表达式
     * @return Locator对象
     */
    public static Locator getPlaywrightLocator(Page page, String selector) {
        return page.locator(selector);
    }

    /**
     * 解析选择器表达式，判断是XPath还是CSS选择器
     * 
     * 自动判断选择器类型，避免手动区分By.xpath和By.cssSelector
     * 
     * @param selector 选择器表达式
     * @return 解析后的By对象
     */
    private static By parseSelector(String selector) {
        if (selector.startsWith("//") || selector.startsWith("(//") || selector.startsWith("/")) {
            return By.xpath(selector);
        } else if (selector.startsWith("[")) {
            return By.cssSelector(selector);
        } else {
            return By.cssSelector(selector);
        }
    }
}