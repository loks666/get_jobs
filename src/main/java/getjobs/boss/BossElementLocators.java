package getjobs.boss;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;

/**
 * Boss直聘网页元素定位器
 * 集中管理所有页面元素的定位表达式
 */
@Slf4j
public class BossElementLocators {
    // 主页相关元素
    public static final String LOGIN_BTN = "//li[@class='nav-figure']";
    public static final String LOGIN_SCAN_SWITCH = "//div[@class='btn-sign-switch ewm-switch' " +
            "or @class='btn-sign-switch phone-switch']";
    public static final String LOGIN_SUCCESS_HEADER = "//*[@id=\"header\"]/div[1]/div[@class='user-nav']/ul/li[@class='nav-figure']";

    /**
     * 搜索结果页相关元素
     */
    // 用于判断岗位列表区块是否加载完成
    public static final String JOB_LIST_CONTAINER = "//div[@class='job-list-container']";
    // 定位job-list-container下的所有岗位卡片（用于循环点击）
    public static final String JOB_LIST_CONTAINER_CARDS = "//div[@class='job-list-container']//li[@class='job-card-box']";

    /**
     * 岗位列表
     */
    // 定位所有岗位卡片，用于获取当前获取到的岗位总数
    public static final String JOB_LIST_SELECTOR = "ul.rec-job-list li.job-card-box";

    // 职位详情页元素
    public static final String CHAT_BUTTON = "a.btn.btn-startchat";
    public static final String ERROR_CONTENT = "//div[@class='error-content']";
    public static final String RECRUITER_INFO = "//div[@class='boss-info-attr']";
    public static final String HR_ACTIVE_TIME = "//span[@class='boss-active-time']";

    // 聊天相关元素
    public static final String DIALOG_TITLE = "//div[@class='dialog-title']";
    public static final String DIALOG_CLOSE = "//i[@class='icon-close']";
    public static final String CHAT_INPUT = "//div[@id='chat-input']";
    public static final String DIALOG_CONTAINER = "//div[@class='dialog-container']";
    public static final String SEND_BUTTON = "//button[@type='send']";
    public static final String IMAGE_UPLOAD = "//div[@aria-label='发送图片']//input[@type='file']";
    public static final String SCROLL_LOAD_MORE = "//div[contains(text(), '滚动加载更多')]";

    // 消息列表页元素
    public static final String CHAT_LIST_ITEM = "//li[@role='listitem']";
    public static final String COMPANY_NAME_IN_CHAT = "//div[@class='title-box']/span[@class='name-box']//span[2]";
    public static final String LAST_MESSAGE = "//div[@class='gray last-msg']/span[@class='last-msg-text']";
    public static final String FINISHED_TEXT = "//div[@class='finished']";

    public static final String DIALOG_CON = ".dialog-con";
    public static final String LOGIN_BTNS = "//div[@class='btns']";
    public static final String PAGE_HEADER = "//h1";
    public static final String ERROR_PAGE_LOGIN = "//a[@ka='403_login']";

    /**
     * 获取所有job-card-box元素并逐个点击
     * 
     * @param page        Playwright页面对象
     * @param delayMillis 每次点击之间的延迟时间（毫秒）
     * @return 成功点击的岗位数量
     */
    public static int clickAllJobCards(Page page, int delayMillis) {
        if (page == null) {
            log.error("Page对象为空，无法执行点击操作");
            return 0;
        }

        try {
            // 使用JOB_LIST_CONTAINER_CARDS定位器获取所有岗位卡片
            Locator jobCards = page.locator(JOB_LIST_CONTAINER_CARDS);
            int cardCount = jobCards.count();

            if (cardCount == 0) {
                log.warn("未找到任何job-card-box元素");
                return 0;
            }

            log.info("找到 {} 个岗位卡片，开始逐个点击", cardCount);
            int successCount = 0;

            // 遍历所有岗位卡片并点击
            for (int i = 0; i < cardCount; i++) {
                try {
                    Locator currentCard = jobCards.nth(i);

                    // 确保元素可见且可点击
                    if (currentCard.isVisible()) {
                        log.debug("正在点击第 {} 个岗位卡片", i + 1);
                        currentCard.click();
                        successCount++;

                        // 添加延迟，避免点击过快
                        if (delayMillis > 0) {
                            Thread.sleep(delayMillis);
                        }
                    } else {
                        log.warn("第 {} 个岗位卡片不可见，跳过", i + 1);
                    }
                } catch (Exception e) {
                    log.error("点击第 {} 个岗位卡片时发生错误: {}", i + 1, e.getMessage());
                }
            }

            log.info("岗位卡片点击完成，成功点击 {} 个，总共 {} 个", successCount, cardCount);
            return successCount;

        } catch (Exception e) {
            log.error("执行岗位卡片点击操作时发生异常: {}", e.getMessage(), e);
            return 0;
        }
    }

}