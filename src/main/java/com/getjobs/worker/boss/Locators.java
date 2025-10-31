package com.getjobs.worker.boss;

/**
 * Boss直聘网页元素定位器
 * 集中管理所有页面元素的定位表达式
 */
public class Locators {
    // 主页相关元素
    public static final String LOGIN_BTN = "//li[@class='nav-figure']";
    public static final String LOGIN_SCAN_SWITCH = "//div[@class='btn-sign-switch ewm-switch']";

    /**
     * 搜索结果页相关元素
     */
    // 用于判断岗位列表区块是否加载完成
    public static final String JOB_LIST_CONTAINER = "//div[@class='job-list-container']";
    // 定位一个岗位卡
    public static final String JOB_CARD_BOX = "li.job-card-box";

    /**
     * 岗位列表
     */
    // 定位所有岗位卡片，用于获取当前获取到的岗位总数
    public static final String JOB_LIST_SELECTOR = "ul.rec-job-list li.job-card-box";
    // 岗位名称
    public static final String JOB_NAME = "a.job-name";
    // 公司名称
    public static final String COMPANY_NAME = "span.boss-name";
    // 公司区域
    public static final String JOB_AREA = "span.company-location";
    // 岗位标签
    public static final String TAG_LIST = "ul.tag-list li";

    // 职位详情页元素
    public static final String CHAT_BUTTON = "[class*='btn btn-startchat']";
    public static final String ERROR_CONTENT = "//div[@class='error-content']";
    public static final String JOB_DETAIL_SALARY = "//div[@class='info-primary']//span[@class='salary']";
    public static final String RECRUITER_INFO = "//div[@class='boss-info-attr']";
    public static final String HR_ACTIVE_TIME = "//span[@class='boss-active-time']";
    public static final String JOB_DESCRIPTION = "//div[@class='job-sec-text']";

    // 聊天相关元素
    public static final String DIALOG_TITLE = "//div[@class='dialog-title']";
    public static final String DIALOG_CLOSE = "//i[@class='icon-close']";
    public static final String CHAT_INPUT = "//div[@id='chat-input']";
    public static final String DIALOG_CONTAINER = "//div[@class='dialog-container']";
    public static final String SEND_BUTTON = "//button[@type='send']";
    public static final String IMAGE_UPLOAD = "//div[@aria-label='发送图片']//input[@type='file']";
    public static final String DIALOG_CONTENT = "//div[@class='dialog-con']";
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

}