package boss;

/**
 * Boss直聘网页元素定位器
 * 集中管理所有页面元素的定位表达式
 */
public class BossElementLocators {
    // 主页相关元素
    // 原始: CHROME_DRIVER.findElement(By.xpath("//li[@class='nav-figure']"))
    public static final String LOGIN_BTN = "//li[@class='nav-figure']";

    // 原始: By.xpath("//div[@class='btn-sign-switch ewm-switch']")
    public static final String LOGIN_SCAN_SWITCH = "//div[@class='btn-sign-switch ewm-switch']";

    // 原始: By.xpath("//*[@id=\"header\"]/div[1]/div[1]/a")
    public static final String LOGIN_SUCCESS_HEADER = "//*[@id=\"header\"]/div[1]/div[1]/a";

    // 原始: By.xpath("//*[@id=\"wrap\"]/div[2]/div[1]/div/div[1]/a[2]")
    public static final String LOGIN_SUCCESS_INDICATOR = "//*[@id=\"wrap\"]/div[2]/div[1]/div/div[1]/a[2]";

    // 搜索结果页相关元素
    // 原始: By.xpath("//div[@class='job-list-container']")
    public static final String JOB_LIST_CONTAINER = "//div[@class='job-list-container']";

    // 原始: page.locator("li.job-card-box")
    public static final String JOB_CARD_BOX = "li.job-card-box";

    // 原始: By.xpath("//div[contains(text(), '没有更多了') or contains(@class,
    // 'job-list-empty')]")
    public static final String NO_MORE_JOBS = "//div[contains(text(), '没有更多了') or contains(@class, 'job-list-empty')]";

    // 原始: By.xpath("//div[contains(text(), '滚动加载更多')]")
    public static final String SCROLL_LOAD_MORE = "//div[contains(text(), '滚动加载更多')]";

    // 职位卡片元素
    // 原始: jobCard.locator("a.job-name")
    public static final String JOB_NAME = "a.job-name";

    // 原始: jobCard.locator("span.boss-name")
    public static final String COMPANY_NAME = "span.boss-name";

    // 原始: jobCard.locator("span.company-location")
    public static final String JOB_AREA = "span.company-location";

    // 原始: jobCard.locator("span.job-salary")
    public static final String JOB_SALARY = "span.job-salary";

    // 原始: jobCard.locator("ul.tag-list li")
    public static final String TAG_LIST = "ul.tag-list li";

    // 职位详情页元素
    // 原始: By.cssSelector("[class*='btn btn-startchat']")
    public static final String CHAT_BUTTON = "[class*='btn btn-startchat']";

    // 原始: SeleniumUtil.findElement("//div[@class='error-content']", "")
    public static final String ERROR_CONTENT = "//div[@class='error-content']";

    // 原始: By.xpath("//div[@class='info-primary']//span[@class='salary']")
    public static final String JOB_DETAIL_SALARY = "//div[@class='info-primary']//span[@class='salary']";

    // 原始: By.xpath("//div[@class='boss-info-attr']")
    public static final String RECRUITER_INFO = "//div[@class='boss-info-attr']";

    // 原始: By.xpath("//span[@class='boss-active-time']")
    public static final String HR_ACTIVE_TIME = "//span[@class='boss-active-time']";

    // 原始: By.xpath("//div[@class='job-sec-text']")
    public static final String JOB_DESCRIPTION = "//div[@class='job-sec-text']";

    // 聊天相关元素
    // 原始: By.xpath("//div[@class='dialog-title']")
    public static final String DIALOG_TITLE = "//div[@class='dialog-title']";

    // 原始: By.xpath("//i[@class='icon-close']")
    public static final String DIALOG_CLOSE = "//i[@class='icon-close']";

    // 原始: By.xpath("//div[@id='chat-input']")
    public static final String CHAT_INPUT = "//div[@id='chat-input']";

    // 原始: By.xpath("//div[@class='dialog-container']")
    public static final String DIALOG_CONTAINER = "//div[@class='dialog-container']";

    // 原始: By.xpath("//button[@type='send']")
    public static final String SEND_BUTTON = "//button[@type='send']";

    // 原始: By.xpath("//div[@aria-label='发送图片']//input[@type='file']")
    public static final String IMAGE_UPLOAD = "//div[@aria-label='发送图片']//input[@type='file']";

    // 原始: By.className("dialog-con")
    public static final String DIALOG_CONTENT = "//div[@class='dialog-con']";

    // 消息列表页元素
    // 原始: By.xpath("//li[@role='listitem']")
    public static final String CHAT_LIST_ITEM = "//li[@role='listitem']";

    // 原始: By.xpath("//div[@class='title-box']/span[@class='name-box']//span[2]")
    public static final String COMPANY_NAME_IN_CHAT = "//div[@class='title-box']/span[@class='name-box']//span[2]";

    // 原始: By.xpath("//div[@class='gray last-msg']/span[@class='last-msg-text']")
    public static final String LAST_MESSAGE = "//div[@class='gray last-msg']/span[@class='last-msg-text']";

    // 原始: By.xpath("//div[@class='finished']")
    public static final String FINISHED_TEXT = "//div[@class='finished']";
}