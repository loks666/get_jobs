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
}
