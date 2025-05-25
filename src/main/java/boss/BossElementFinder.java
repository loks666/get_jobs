package boss;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Boss直聘元素查找工具类
 * 封装Selenium和Playwright元素查找逻辑
 * <p>
 * 替代原来代码中直接使用的:
 * - CHROME_DRIVER.findElement(By.xpath("..."))
 * - CHROME_DRIVER.findElement(By.cssSelector("..."))
 * - WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("...")))
 * - SeleniumUtil.findElement("...", "")
 */
public class BossElementFinder {
    private static final Logger log = LoggerFactory.getLogger(BossElementFinder.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;


    /**
     * 基于Playwright查找单个元素
     * <p>
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
     * <p>
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
     * <p>
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

}