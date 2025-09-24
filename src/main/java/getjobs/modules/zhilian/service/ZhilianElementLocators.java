package getjobs.modules.zhilian.service;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 智联招聘网站元素定位器
 * 用于定位智联招聘网站上的各种元素和解析职位信息
 * 
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Slf4j
public class ZhiLianElementLocators {

    // ==================== 页面元素定位器 ====================
    
    /** 登录按钮 */
    public static final String LOGIN_BUTTON = "a[href*='login']";
    
    /** 登录/注册按钮 */
    public static final String LOGIN_REGISTER_BUTTON = "a.home-header__c-no-login";
    
    /** 我要招人按钮（已登录状态） */
    public static final String RECRUITER_BUTTON = "a.home-header__b-login";
    
    /** 用户信息区域 */
    public static final String USER_INFO_AREA = "div.user-info";
    
    /** 页面头部右侧区域 */
    public static final String HEADER_RIGHT_AREA = "div.home-header__right";
    
    /** 投递按钮 */
    public static final String APPLY_BUTTON = "button.apply-btn, a.apply-btn";
    
    /** 已投递标记 */
    public static final String APPLIED_MARK = "span.applied-mark";
    
    /** 用户登录后的用户名元素 */
    public static final String USER_WELCOME_USERNAME = "div.zp-welcome__username";
    
    /** 退出按钮 */
    public static final String LOGOUT_BUTTON = "a#logout";

    // ==================== 职位采集方法 ====================

    /**
     * 根据页码点击分页元素
     * 在class="soupager"的div元素中查找指定页码的a元素并点击
     * 
     * @param page       Playwright页面对象
     * @param pageNumber 要点击的页码
     * @return true表示点击成功，false表示未找到对应页码或点击失败
     */
    public static boolean clickPageNumber(Page page, int pageNumber) {
        try {
            // 首先等待分页元素出现
            try {
                page.waitForSelector("div.soupager", new Page.WaitForSelectorOptions().setTimeout(8000));
            } catch (Exception e) {
                log.error("等待分页元素出现超时: {}", e.getMessage());
                return false;
            }
            
            // 查找class="soupager"的div元素
            Locator pagerElement = page.locator("div.soupager");
            
            if (pagerElement.count() == 0) {
                log.error("未找到分页元素 div.soupager");
                return false;
            }

            // 查找包含指定页码文本的a元素，使用精确匹配避免误匹配
            Locator pageElement = pagerElement.locator("a.soupager__index").
                    getByText(String.valueOf(pageNumber), new Locator.GetByTextOptions().setExact(true));

            if (pageElement.count() == 0) {
                log.info("未找到页码为 {} 的分页元素，可能已到达最后一页", pageNumber);
                return false;
            }

            // 检查元素是否已经是当前激活状态
            Locator activePageElement = pagerElement.locator("a.soupager__index--active");
            if (activePageElement.count() > 0) {
                String activePageText = activePageElement.textContent().trim();
                if (String.valueOf(pageNumber).equals(activePageText)) {
                    log.info("页码 {} 已经是当前激活状态，无需点击", pageNumber);
                    return true;
                }
            }

            // 确保元素可见且可点击
            pageElement.scrollIntoViewIfNeeded();
            
            // 在点击之前添加随机延迟3-5秒，模拟真实用户行为
            Random random = new Random();
            int delay = 3000 + random.nextInt(2001); // 3000-5000毫秒之间的随机延迟
            log.info("准备点击页码 {}，随机延迟 {} 毫秒", pageNumber, delay);
            page.waitForTimeout(delay);
            
            // 点击页码元素
            pageElement.click();
            
            // 等待页面状态变化，确保点击生效
            try {
                // 等待当前页码变为激活状态
                page.waitForSelector("div.soupager a.soupager__index--active:has-text('" + pageNumber + "')", 
                    new Page.WaitForSelectorOptions().setTimeout(5000));
                log.info("成功点击页码: {}，页面已切换", pageNumber);
            } catch (Exception e) {
                log.warn("等待页码 {} 激活状态超时，但点击操作已执行: {}", pageNumber, e.getMessage());
            }
            
            return true;

        } catch (Exception e) {
            log.error("点击页码 {} 时发生错误: {}", pageNumber, e.getMessage());
            return false;
        }
    }

    /**
     * 获取当前激活的页码
     * 查找class="soupager"中带有"soupager__index--active"类的a元素，获取其页码值
     * 
     * @param page Playwright页面对象
     * @return 当前激活的页码，如果未找到则返回-1
     */
    public static int getCurrentPageNumber(Page page) {
        try {
            // 查找class="soupager"的div元素
            Locator pagerElement = page.locator("div.soupager");
            
            if (pagerElement.count() == 0) {
                log.error("未找到分页元素 div.soupager");
                return -1;
            }

            // 查找当前激活的页码元素
            Locator activePageElement = pagerElement.locator("a.soupager__index--active");
            
            if (activePageElement.count() > 0) {
                String pageText = activePageElement.textContent().trim();
                try {
                    int currentPage = Integer.parseInt(pageText);
                    log.info("当前激活页码: {}", currentPage);
                    return currentPage;
                } catch (NumberFormatException e) {
                    log.error("解析当前页码失败: {}", pageText);
                    return -1;
                }
            }

            log.error("未找到当前激活的页码元素");
            return -1;

        } catch (Exception e) {
            log.error("获取当前页码时发生错误: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * 获取所有可见的页码列表
     * 从class="soupager"的div元素中获取所有a.soupager__index元素的页码值
     * 
     * @param page Playwright页面对象
     * @return 所有可见页码的列表，如果出错则返回空列表
     */
    public static List<Integer> getVisiblePageNumbers(Page page) {
        List<Integer> pageNumbers = new ArrayList<>();
        
        try {
            // 查找class="soupager"的div元素
            Locator pagerElement = page.locator("div.soupager");
            
            if (pagerElement.count() == 0) {
                log.error("未找到分页元素 div.soupager");
                return pageNumbers;
            }

            // 获取所有页码元素
            Locator pageElements = pagerElement.locator("a.soupager__index");
            int elementCount = pageElements.count();
            
            for (int i = 0; i < elementCount; i++) {
                Locator pageElement = pageElements.nth(i);
                String pageText = pageElement.textContent().trim();
                
                try {
                    int pageNumber = Integer.parseInt(pageText);
                    pageNumbers.add(pageNumber);
                } catch (NumberFormatException e) {
                    log.warn("跳过非数字页码: {}", pageText);
                }
            }
            
            log.info("获取到可见页码列表: {}", pageNumbers);

        } catch (Exception e) {
            log.error("获取可见页码列表时发生错误: {}", e.getMessage());
        }
        
        return pageNumbers;
    }

    /**
     * 检查是否需要登录
     * 
     * @param page Playwright页面对象
     * @return true表示需要登录，false表示已登录
     */
    public static boolean isLoginRequired(Page page) {
        try {
            // 等待页面加载完成
            log.debug("等待页面加载完成...");
            page.waitForLoadState();
            
            // 等待DOM稳定，确保所有元素都已加载
            page.waitForTimeout(2000);
            
            // 首先尝试等待页面头部右侧区域加载
            try {
                page.waitForSelector(HEADER_RIGHT_AREA, new Page.WaitForSelectorOptions().setTimeout(5000));
                log.debug("页面头部右侧区域已加载");
            } catch (Exception e) {
                log.debug("页面头部右侧区域未在5秒内加载完成，继续检查");
            }
            
            // 检查页面头部右侧区域是否存在
            Locator headerRightArea = page.locator(HEADER_RIGHT_AREA);
            if (!headerRightArea.isVisible()) {
                log.debug("未找到页面头部右侧区域，尝试其他检查方法");
                // 如果头部区域不存在，使用原有的检查方法
                Locator loginButton = page.locator(LOGIN_BUTTON);
                if (loginButton.isVisible()) {
                    log.debug("找到传统登录按钮，需要登录");
                    return true;
                }
                
                Locator userInfo = page.locator(USER_INFO_AREA);
                boolean isLoggedIn = userInfo.isVisible();
                log.debug("用户信息区域可见性: {}", isLoggedIn);
                return !isLoggedIn;
            }
            
            // 等待一下确保头部区域内的元素加载完成
            page.waitForTimeout(1000);
            
            // 检查是否存在"登录/注册"按钮
            Locator loginRegisterButton = page.locator(LOGIN_REGISTER_BUTTON);
            if (loginRegisterButton.isVisible()) {
                log.debug("找到'登录/注册'按钮，需要登录");
                return true;
            }
            
            // 检查是否存在"我要招人"按钮（已登录状态的标识）
            Locator recruiterButton = page.locator(RECRUITER_BUTTON);
            if (recruiterButton.isVisible()) {
                log.debug("找到'我要招人'按钮，已登录状态");
                return false;
            }
            
            // 如果以上都没有找到，可能页面结构发生变化，使用备用检查方法
            log.debug("页面结构可能发生变化，使用备用检查方法");
            
            // 等待一下再进行备用检查
            page.waitForTimeout(1000);
            
            // 检查是否存在传统的登录按钮
            Locator loginButton = page.locator(LOGIN_BUTTON);
            if (loginButton.isVisible()) {
                log.debug("找到传统登录按钮，需要登录");
                return true;
            }
            
            // 检查是否存在用户信息区域
            Locator userInfo = page.locator(USER_INFO_AREA);
            boolean isLoggedIn = userInfo.isVisible();
            log.debug("备用检查 - 用户信息区域可见性: {}", isLoggedIn);
            return !isLoggedIn;
            
        } catch (Exception e) {
            log.error("检查登录状态失败", e);
            return true; // 出错时默认需要登录
        }
    }

    /**
     * 点击投递按钮
     * 
     * @param page Playwright页面对象
     * @return true表示投递成功，false表示投递失败
     */
    public static boolean clickApplyButton(Page page) {
        try {
            // 检查是否已经投递过
            Locator appliedMark = page.locator(APPLIED_MARK);
            if (appliedMark.isVisible()) {
                log.info("该职位已投递过");
                return false;
            }
            
            // 点击投递按钮
            Locator applyButton = page.locator(APPLY_BUTTON);
            if (applyButton.isVisible()) {
                applyButton.click();
                page.waitForTimeout(1000);
                log.info("投递成功");
                return true;
            } else {
                log.warn("未找到投递按钮");
                return false;
            }
            
        } catch (Exception e) {
            log.error("投递失败", e);
            return false;
        }
    }


    /**
     * 判断用户是否已成功完成登录
     * 通过检查用户名元素或退出按钮的存在来判断登录状态
     * 
     * @param page Playwright页面对象
     * @return true表示已登录，false表示未登录
     */
    public static boolean isUserLoggedIn(Page page) {
        try {
            // 等待页面加载完成
            page.waitForLoadState();
            
            // 等待DOM稳定，确保所有元素都已加载
            page.waitForTimeout(2000);
            
            log.debug("检查用户登录状态...");
            
            // 方法1：检查用户名元素是否存在
            Locator usernameElement = page.locator(USER_WELCOME_USERNAME);
            if (usernameElement.isVisible()) {
                String username = usernameElement.textContent();
                log.debug("找到用户名元素，用户已登录，用户名: {}", username);
                return true;
            }
            
            // 方法2：检查退出按钮是否存在（在用户名下拉菜单中）
            Locator logoutButton = page.locator(LOGOUT_BUTTON);
            if (logoutButton.isVisible()) {
                log.debug("找到退出按钮，用户已登录");
                return true;
            }
            
            // 如果退出按钮不可见，可能需要先点击用户名区域展开菜单
            if (usernameElement.count() > 0) {
                try {
                    // 点击用户名区域展开下拉菜单
                    usernameElement.click();
                    page.waitForTimeout(500);
                    
                    // 再次检查退出按钮
                    if (logoutButton.isVisible()) {
                        log.debug("展开菜单后找到退出按钮，用户已登录");
                        // 点击其他地方收起菜单
                        page.click("body");
                        return true;
                    }
                } catch (Exception e) {
                    log.debug("尝试展开用户菜单失败: {}", e.getMessage());
                }
            }
            
            log.debug("未找到登录状态标识，用户未登录");
            return false;
            
        } catch (Exception e) {
            log.error("检查用户登录状态失败", e);
            return false; // 出错时默认未登录
        }
    }
}