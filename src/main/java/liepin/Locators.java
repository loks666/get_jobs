package liepin;

/**
 * 猎聘网页元素定位器
 * 集中管理所有页面元素的定位表达式
 */
public class Locators {
    // 主页相关元素
    public static final String HEADER_LOGO = "#header-logo-box";
    public static final String LOGIN_SWITCH_BTN = "//div[@class='jsx-263198893 btn-sign-switch']";
    public static final String LOGIN_BUTTONS = "//button[@type='button']";
    public static final String USER_INFO = "//div[@id='header-quick-menu-user-info']";

    // 搜索结果页相关元素
    public static final String PAGINATION_BOX = ".list-pagination-box";
    public static final String PAGINATION_ITEMS = ".list-pagination-box li";
    public static final String NEXT_PAGE = "li[title='Next Page']";
    public static final String SUBSCRIBE_CLOSE_BTN = "//div[contains(@class, 'subscribe-close-btn')]";

    // 岗位列表相关元素
    public static final String JOB_CARDS = "//div[contains(@class, 'job-card-pc-container')]";
    public static final String JOB_TITLE = "//div[contains(@class, 'job-title-box')]";
    public static final String COMPANY_NAME = "//span[contains(@class, 'company-name')]";
    public static final String JOB_SALARY = "//span[contains(@class, 'job-salary')]";

    // 聊天相关元素
    public static final String CHAT_BUTTON_PRIMARY = "//button[@class='ant-btn ant-btn-primary ant-btn-round']";
    public static final String CHAT_BUTTON_ALTERNATIVE = "//button[@class='ant-btn ant-btn-round ant-btn-primary']";
    public static final String CHAT_HEADER = ".__im_basic__header-wrap";
    public static final String CHAT_TEXTAREA = "//textarea[contains(@class, '__im_basic__textarea')]";
    public static final String CHAT_CLOSE = "div.__im_basic__contacts-title svg";
    public static final String RECRUITER_INFO = "//div[contains(@class, 'recruiter-info-box')]";
}