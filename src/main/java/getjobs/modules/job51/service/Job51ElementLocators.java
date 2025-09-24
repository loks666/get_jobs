package getjobs.modules.job51.service;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Locator;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * 51Job网站元素定位器
 * 用于定位51Job网站上的各种元素
 */
@Slf4j
public class Job51ElementLocators {
    /**
     * 判断用户是否已登录
     * 通过检查class="user"块中是否存在"退出帐号"按钮来判断登录状态
     *
     * @param page Playwright页面对象
     * @return true表示已登录，false表示未登录
     */
    public static boolean isUserLoggedIn(Page page) {
        try {
            // 查找class="user"的div元素
            var userDiv = page.locator("div.user");

            // 检查是否存在"退出帐号"链接
            var logoutLink = userDiv.locator("a:has-text('退出帐号')");

            // 如果找到退出登录按钮，说明用户已登录
            return logoutLink.count() > 0;
        } catch (Exception e) {
            // 如果出现异常，默认认为未登录
            return false;
        }
    }


    /**
     * 获取用户信息
     * 从class="user"块中提取用户名等信息
     * 
     * @param page Playwright页面对象
     * @return 用户名，如果未登录或获取失败则返回null
     */
    public static String getUserName(Page page) {
        try {
            var userDiv = page.locator("div.user");
            var userNameElement = userDiv.locator("a.uname");

            if (userNameElement.count() > 0) {
                return userNameElement.textContent();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 筛选条件信息类
     * 用于存储每个筛选条件的标签和对应的选项
     */
    public static class FilterOption {
        private String label; // 筛选条件标签（如：工作地点、月薪范围等）
        private String text; // span元素的文本内容
        private Locator spanElement; // span元素定位器
        private Locator clistElement; // clist容器元素定位器

        public FilterOption(String label, String text, Locator spanElement, Locator clistElement) {
            this.label = label;
            this.text = text;
            this.spanElement = spanElement;
            this.clistElement = clistElement;
        }

        // Getters
        public String getLabel() {
            return label;
        }

        public String getText() {
            return text;
        }

        public Locator getSpanElement() {
            return spanElement;
        }

        public Locator getClistElement() {
            return clistElement;
        }
    }

    /**
     * 获取页面中class="j_filter"下所有筛选条件元素
     * 解析每个fbox中的label和clist，获取clist下所有span元素的文本内容
     * 
     * @param page Playwright页面对象
     * @return 筛选条件映射表，key为label文本，value为该label下的所有选项列表
     */
    public static Map<String, List<FilterOption>> getFilterOptions(Page page) {
        Map<String, List<FilterOption>> filterOptionsMap = new HashMap<>();

        try {
            // 获取class="j_filter"下的所有div class="fbox"元素
            var fboxElements = page.locator("div.j_filter div.fbox");
            int fboxCount = fboxElements.count();

            for (int i = 0; i < fboxCount; i++) {
                var fbox = fboxElements.nth(i);

                // 获取fbox下的label元素
                var labelElement = fbox.locator("div.label");
                if (labelElement.count() > 0) {
                    String labelText = labelElement.textContent().trim();

                    // 获取fbox下的clist元素
                    var clistElement = fbox.locator("div.clist");
                    if (clistElement.count() > 0) {
                        List<FilterOption> options = new ArrayList<>();

                        // 获取clist下的所有span元素
                        var spanElements = clistElement.locator("span");
                        int spanCount = spanElements.count();

                        for (int j = 0; j < spanCount; j++) {
                            var spanElement = spanElements.nth(j);
                            String spanText = spanElement.textContent().trim();

                            // 创建FilterOption对象
                            FilterOption option = new FilterOption(labelText, spanText, spanElement, clistElement);
                            options.add(option);
                        }

                        // 将选项列表添加到映射表中
                        filterOptionsMap.put(labelText, options);
                    }
                }
            }

        } catch (Exception e) {
            log.error("获取筛选条件时发生错误: {}", e.getMessage());
        }

        return filterOptionsMap;
    }

    /**
     * 根据标签和文本内容查找对应的span元素
     * 
     * @param page       Playwright页面对象
     * @param labelText  筛选条件标签文本（如："工作地点："）
     * @param optionText 要查找的选项文本（如："广州"）
     * @return 匹配的span元素定位器，如果未找到则返回null
     */
    public static Locator findSpanByLabelAndText(Page page, String labelText, String optionText) {
        try {
            // 获取class="j_filter"下的所有div class="fbox"元素
            var fboxElements = page.locator("div.j_filter div.fbox");
            int fboxCount = fboxElements.count();

            for (int i = 0; i < fboxCount; i++) {
                var fbox = fboxElements.nth(i);

                // 检查label是否匹配
                var labelElement = fbox.locator("div.label");
                if (labelElement.count() > 0) {
                    String currentLabelText = labelElement.textContent().trim();

                    if (labelText.equals(currentLabelText)) {
                        // 找到匹配的label，在其clist中查找span
                        var clistElement = fbox.locator("div.clist");
                        if (clistElement.count() > 0) {
                            var spanElements = clistElement.locator("span");
                            int spanCount = spanElements.count();

                            for (int j = 0; j < spanCount; j++) {
                                var spanElement = spanElements.nth(j);
                                String spanText = spanElement.textContent().trim();

                                if (optionText.equals(spanText)) {
                                    return spanElement;
                                }
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("查找span元素时发生错误: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 获取指定ID的input元素并模拟用户输入文本
     * 输入完成后自动按回车键确认
     * 
     * @param page      Playwright页面对象
     * @param inputId   input元素的ID属性值
     * @param inputText 要输入的文本内容
     * @return true表示输入成功，false表示输入失败
     */
    public static boolean inputTextAndSubmit(Page page, String inputId, String inputText) {
        try {
            // 根据ID定位input元素
            var inputElement = page.locator("#" + inputId);

            // 检查元素是否存在
            if (inputElement.count() == 0) {
                log.error("未找到ID为 '{}' 的input元素", inputId);
                return false;
            }

            // 清空输入框内容（如果有的话）
            inputElement.clear();

            // 模拟用户输入文本
            inputElement.fill(inputText);

            // 等待一小段时间确保输入完成
            page.waitForTimeout(500);

            // 按回车键提交
            inputElement.press("Enter");

            log.info("成功在ID为 '{}' 的input元素中输入文本: {}", inputId, inputText);
            return true;

        } catch (Exception e) {
            log.error("输入文本时发生错误: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 专门用于keywordInput输入框的方法
     * 获取id="keywordInput"的input元素并模拟用户录入输入对应的文本并回车确定
     * 
     * @param page    Playwright页面对象
     * @param keyword 要搜索的关键词
     * @return true表示输入成功，false表示输入失败
     */
    public static boolean inputKeywordAndSearch(Page page, String keyword) {
        return inputTextAndSubmit(page, "keywordInput", keyword);
    }

    /**
     * 判断首页是否存在登录元素
     * 通过检查class="login loginBtnClick"的span元素来判断是否存在登录按钮
     * 
     * @param page Playwright页面对象
     * @return true表示存在登录元素，false表示不存在
     */
    public static boolean hasLoginElement(Page page) {
        try {
            // 查找class="login loginBtnClick"的span元素
            var loginElement = page.locator("span.login.loginBtnClick");

            // 检查元素是否存在且文本内容为"登录/注册"
            if (loginElement.count() > 0) {
                String elementText = loginElement.textContent();
                return "登录/注册".equals(elementText);
            }

            return false;
        } catch (Exception e) {
            log.error("检查登录元素时发生错误: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 根据页码点击分页元素
     * 在class="el-pager"的ul元素中查找指定页码的li元素并点击
     * 
     * @param page       Playwright页面对象
     * @param pageNumber 要点击的页码
     * @return true表示点击成功，false表示未找到对应页码或点击失败
     */
    public static boolean clickPageNumber(Page page, int pageNumber) {
        try {
            // 首先等待分页元素出现
            try {
                page.waitForSelector("ul.el-pager", new Page.WaitForSelectorOptions().setTimeout(8000));
            } catch (Exception e) {
                log.error("等待分页元素出现超时: {}", e.getMessage());
                return false;
            }
            
            // 查找class="el-pager"的ul元素
            var pagerElement = page.locator("ul.el-pager");
            
            if (pagerElement.count() == 0) {
                log.error("未找到分页元素 ul.el-pager");
                return false;
            }

            // 查找包含指定页码文本的li元素
//            var pageElement = pagerElement.locator("li.number").filter(
//                new Locator.FilterOptions().setHasText(String.valueOf(pageNumber))
//            );
            /**
             * Error {
             *   message='Error: strict mode violation: locator("ul.el-pager").locator("li.number").filter(new Locator.FilterOptions().setHasText("2")) resolved to 2 elements:
             *     1) <li class="number">2</li> aka getByText("2", new Page.GetByTextOptions().setExact(true))
             *     2) <li class="number">25</li> aka getByText("25", new Page.GetByTextOptions().setExact(true))
             */
            var pageElement = pagerElement.locator("li.number").
                    getByText(String.valueOf(pageNumber), new Locator.GetByTextOptions().setExact(true));



            if (pageElement.count() == 0) {
                log.info("未找到页码为 {} 的分页元素，可能已到达最后一页", pageNumber);
                return false;
            }

            // 检查元素是否已经是当前激活状态
            var activePageElement = pagerElement.locator("li.number.active");
            if (activePageElement.count() > 0) {
                String activePageText = activePageElement.textContent().trim();
                if (String.valueOf(pageNumber).equals(activePageText)) {
                    log.info("页码 {} 已经是当前激活状态，无需点击", pageNumber);
                    return true;
                }
            }

            // 确保元素可见且可点击
            pageElement.scrollIntoViewIfNeeded();
            
            // 点击页码元素
            pageElement.click();
            
            // 等待页面状态变化，确保点击生效
            try {
                // 等待当前页码变为激活状态
                page.waitForSelector("ul.el-pager li.number.active:has-text('" + pageNumber + "')", 
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
     * 查找class="el-pager"中带有"active"类的li元素，获取其页码值
     * 
     * @param page Playwright页面对象
     * @return 当前激活的页码，如果未找到则返回-1
     */
    public static int getCurrentPageNumber(Page page) {
        try {
            // 查找class="el-pager"的ul元素
            var pagerElement = page.locator("ul.el-pager");
            
            if (pagerElement.count() == 0) {
                log.error("未找到分页元素 ul.el-pager");
                return -1;
            }

            // 查找当前激活的页码元素
            var activePageElement = pagerElement.locator("li.number.active");
            
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
     * 从class="el-pager"的ul元素中获取所有li.number元素的页码值
     * 
     * @param page Playwright页面对象
     * @return 所有可见页码的列表，如果出错则返回空列表
     */
    public static List<Integer> getVisiblePageNumbers(Page page) {
        List<Integer> pageNumbers = new ArrayList<>();
        
        try {
            // 查找class="el-pager"的ul元素
            var pagerElement = page.locator("ul.el-pager");
            
            if (pagerElement.count() == 0) {
                log.error("未找到分页元素 ul.el-pager");
                return pageNumbers;
            }

            // 获取所有页码元素
            var pageElements = pagerElement.locator("li.number");
            int elementCount = pageElements.count();
            
            for (int i = 0; i < elementCount; i++) {
                var pageElement = pageElements.nth(i);
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
     * 判断登录页是否存在密码登录元素
     * 通过检查class="login_qr"的div元素来判断是否在登录页面
     * 存在表示未登录，需要进行登录操作
     * 
     * @param page Playwright页面对象
     * @return true表示存在密码登录元素（未登录状态），false表示不存在（已登录或非登录页）
     */
    public static boolean hasPasswordLoginElement(Page page) {
        try {
            // 查找class="login_qr"的div元素
            var loginQrElement = page.locator("div.login_qr");

            // 检查元素是否存在
            if (loginQrElement.count() > 0) {
                // 进一步验证内部结构，确保这是登录页面的二维码登录区域
                var qrIniElement = loginQrElement.locator("div.qrini");
                var titleElement = qrIniElement.locator("p.tit");
                
                if (qrIniElement.count() > 0 && titleElement.count() > 0) {
                    // 检查标题文本是否包含"微信扫码登录"
                    String titleText = titleElement.textContent();
                    if (titleText != null && titleText.contains("微信扫码登录")) {
                        log.info("检测到登录页面的密码登录元素，用户未登录");
                        return true;
                    }
                }
            }

            log.info("未检测到登录页面的密码登录元素");
            return false;
            
        } catch (Exception e) {
            log.error("检查密码登录元素时发生错误: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 点击岗位详情页的申请职位按钮
     * 定位并点击class="but_sq"且id="app_ck"的申请职位链接
     * 
     * @param page Playwright页面对象
     * @return true表示点击成功，false表示未找到申请按钮或点击失败
     */
    public static boolean clickApplyJobButton(Page page) {
        try {
            // 等待页面加载完成，给申请职位按钮充分的加载时间
            try {
                page.waitForSelector("a.but_sq#app_ck", new Page.WaitForSelectorOptions().setTimeout(5000));
            } catch (Exception e) {
                log.warn("等待申请职位按钮加载超时(5秒)，按钮可能不存在: {}", e.getMessage());
                return false;
            }
            
            // 查找申请职位按钮：class="but_sq" 且 id="app_ck"
            var applyButton = page.locator("a.but_sq#app_ck");
            
            // 再次检查元素是否存在（经过等待后）
            if (applyButton.count() == 0) {
                log.error("等待后仍未找到申请职位按钮");
                return false;
            }
            
            // 验证按钮文本内容是否包含"申请职位"
            String buttonText = applyButton.textContent();
            if (buttonText == null || !buttonText.contains("申请职位")) {
                log.error("找到元素但文本内容不匹配，实际文本: {}", buttonText);
                return false;
            }
            
            // 确保元素可见
            applyButton.scrollIntoViewIfNeeded();
            
            // 点击申请职位按钮
            applyButton.click();
            
            log.info("成功点击申请职位按钮");
            
            // 等待一小段时间让页面响应点击事件
            page.waitForTimeout(1000);
            
            return true;
            
        } catch (Exception e) {
            log.error("点击申请职位按钮时发生错误: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查岗位详情页是否存在申请职位按钮
     * 通过检查class="but_sq"且id="app_ck"的元素来判断是否在岗位详情页
     * 
     * @param page Playwright页面对象
     * @return true表示存在申请职位按钮，false表示不存在
     */
    public static boolean hasApplyJobButton(Page page) {
        try {
            // 等待页面加载完成，给申请职位按钮充分的加载时间
            try {
                page.waitForSelector("a.but_sq#app_ck", new Page.WaitForSelectorOptions().setTimeout(5000));
            } catch (Exception e) {
                log.info("等待申请职位按钮加载超时(5秒)，按钮可能不存在: {}", e.getMessage());
                return false;
            }
            
            // 查找申请职位按钮
            var applyButton = page.locator("a.but_sq#app_ck");
            
            if (applyButton.count() > 0) {
                // 验证按钮文本内容
                String buttonText = applyButton.textContent();
                boolean hasCorrectText = buttonText != null && buttonText.contains("申请职位");
                
                log.info("检测到申请职位按钮，文本内容: {}, 文本匹配: {}", buttonText, hasCorrectText);
                return hasCorrectText;
            }
            
            log.info("未检测到申请职位按钮");
            return false;
            
        } catch (Exception e) {
            log.error("检查申请职位按钮时发生错误: {}", e.getMessage());
            return false;
        }
    }
}
