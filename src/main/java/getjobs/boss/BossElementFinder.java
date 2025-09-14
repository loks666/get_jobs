package getjobs.boss;

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