package bossRebuild.constants;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;

public class Elements {
    // 配置文件路径
    private static final String ELEMENTS_CONFIG_PATH = "elements.json";
    private static final JSONObject elementsConfig;

    // 静态初始化块，从 elements.json 加载配置
    static {
        JSONObject tempConfig;
        try {
            InputStream is = Elements.class.getClassLoader().getResourceAsStream(ELEMENTS_CONFIG_PATH);
            if (is == null) {
                throw new IOException("Cannot find elements.json in classpath");
            }
            String jsonContent = new String(is.readAllBytes());
            System.out.println("Loaded elements.json content: " + jsonContent);
            tempConfig = new JSONObject(jsonContent);
            System.out.println("Parsed elementsConfig: " + tempConfig.toString(2));
        } catch (IOException e) {
            System.err.println("Failed to load elements.json, using default values: " + e.getMessage());
            tempConfig = new JSONObject();
        }
        elementsConfig = tempConfig;
    }

    // 获取元素的方法
    private static String getElement(String pageKey, String elementKey, String defaultValue) {
        System.out.println("Fetching element for pageKey: " + pageKey + ", elementKey: " + elementKey);
        JSONObject pageConfig = elementsConfig.optJSONObject(pageKey);
        if (pageConfig == null) {
            System.out.println("No pageConfig found for pageKey: " + pageKey + ", returning default: " + defaultValue);
            return defaultValue;
        }
        JSONObject elements = pageConfig.optJSONObject("elements");
        if (elements == null) {
            System.out.println("No elements found for pageKey: " + pageKey + ", returning default: " + defaultValue);
            return defaultValue;
        }
        JSONObject element = elements.optJSONObject(elementKey);
        if (element == null) {
            System.out.println("No element found for elementKey: " + elementKey + ", returning default: " + defaultValue);
            return defaultValue;
        }
        String value = element.optString("value", defaultValue);
        System.out.println("Found element value: " + value);
        return value;
    }

    // 首页相关元素 (https://www.zhipin.com)
    /** 登录按钮的 CSS 选择器，用于检测页面是否需要登录 */
    public static final String LOGIN_BUTTON_CSS = getElement("homePage", "loginButton", "//*[@id=\"header\"]/div[1]/div[4]/div/a");

    // 登录页面相关元素 (https://www.zhipin.com/web/user/?ka=header-login)
    /** 403 登录提示的 XPath，用于检测 403 错误并跳转登录 */
    public static final String LOGIN_403_XPATH = getElement("loginPage", "login403", "//a[@ka='403_login']");
    /** 登录页面标题的 XPath，用于检测是否进入登录页面 */
    public static final String LOGIN_HEADER_XPATH = getElement("loginPage", "loginHeader", "//h1");
    /** 导航栏用户信息的 XPath，用于检测是否已登录 */
    public static final String NAV_FIGURE_XPATH = getElement("loginPage", "navFigure", "//li[@class='nav-figure']");
    /** 二维码登录切换按钮的 XPath，用于切换到二维码登录方式 */
    public static final String EWM_SWITCH_XPATH = getElement("loginPage", "ewmSwitch", "//*[@id=\"wrap\"]/div/div[2]/div[2]/div[1]");
    /** 登录成功后头部元素的 XPath，用于确认登录成功 */
    public static final String HEADER_LOGIN_XPATH = getElement("loginPage", "headerLogin", "//*[@id=\"header\"]/div[1]/div[1]/a");
    /** 登录成功后页面元素的 XPath，用于进一步确认登录成功 */
    public static final String LOGIN_SUCCESS_XPATH = getElement("loginPage", "loginSuccess", "//*[@id=\"wrap\"]/div[2]/div[1]/div/div[1]/a[2]");

    // 职位搜索页面相关元素 (https://www.zhipin.com/web/geek/job?...)
    /** 职位列表的 XPath，用于定位搜索结果中的职位列表容器 */
    public static final String JOB_LIST_XPATH = getElement("jobSearchPage", "jobList", "//*[@id=\"wrap\"]/div[2]/div[3]/div/div/div[1]");
    /** 职位卡片的 CSS 选择器，用于定位单个职位卡片 */
    public static final String JOB_CARD_CSS = getElement("jobSearchPage", "jobCard", "li.job-card-wrapper");
    /** 职位薪资的 CSS 选择器，用于获取职位薪资信息 */
    public static final String JOB_SALARY_CSS = getElement("jobSearchPage", "jobSalary", "span.job-salary");
    /** 职位名称的 CSS 选择器，用于获取职位名称 */
    public static final String JOB_NAME_CSS = getElement("jobSearchPage", "jobName", "a.job-name");
    /** 公司名称的 CSS 选择器，用于获取公司名称 */
    public static final String COMPANY_NAME_CSS = getElement("jobSearchPage", "companyName", "span.company-name");
    /** 职位所在区域的 CSS 选择器，用于获取职位所在城市或区域 */
    public static final String JOB_AREA_CSS = getElement("jobSearchPage", "jobArea", "span.job-area");
    /** 职位标签的 CSS 选择器，用于获取职位标签（如经验要求、学历要求等） */
    public static final String JOB_TAGS_CSS = getElement("jobSearchPage", "jobTags", "ul.tag-list li");
    /** 职位链接的 CSS 选择器，用于获取职位详情页的 URL */
    public static final String JOB_HREF_CSS = getElement("jobSearchPage", "jobHref", "a.job-name");
    /** 下一页按钮的 XPath，用于定位“下一页”按钮 */
    public static final String NEXT_PAGE_XPATH = getElement("jobSearchPage", "nextPage", "//a//i[@class='ui-icon-arrow-right']");
    /** 职位列表包裹容器的 XPath，用于等待页面加载完成 */
    public static final String JOB_LIST_WRAPPER_XPATH = getElement("jobSearchPage", "jobListWrapper", "//ul[contains(@class, 'job-list-box')]");

    // 职位详情页面相关元素 (https://www.zhipin.com/job_detail/...)
    /** 聊天按钮的 CSS 选择器，用于定位“立即沟通”按钮 */
    public static final String CHAT_BUTTON_CSS = getElement("jobDetailPage", "chatButton", "[class*='btn btn-startchat']");
    /** 职位描述的 XPath，用于获取职位详情页中的职位描述文本 */
    public static final String JOB_DESCRIPTION_XPATH = getElement("jobDetailPage", "jobDescription", "//div[contains(@class, 'job-sec-text')]");
    /** 异常访问提示的 XPath，用于检测页面是否出现“异常访问”错误 */
    public static final String ERROR_CONTENT_XPATH = getElement("jobDetailPage", "errorContent", "//div[@class='error-content']");
    /** HR 活跃时间的 XPath，用于获取 HR 的活跃状态（如“半年前活跃”） */
    public static final String ACTIVE_TIME_XPATH = getElement("jobDetailPage", "activeTime", "//span[@class='boss-active-time']");
    /** 公司和 HR 信息的 XPath，用于获取公司和 HR 的综合信息 */
    public static final String COMPANY_AND_HR_XPATH = getElement("jobDetailPage", "companyAndHR", "//div[@class='boss-info-attr']");
    /** 聊天输入框的 XPath，用于定位聊天输入框 */
    public static final String CHAT_INPUT_XPATH = getElement("jobDetailPage", "chatInput", "//div[@id='chat-input']");
    /** 发送按钮的 XPath，用于定位聊天窗口中的“发送”按钮 */
    public static final String SEND_BUTTON_XPATH = getElement("jobDetailPage", "sendButton", "//button[@type='send']");
    /** 对话容器的 XPath，用于检测对话框状态（如“不匹配”提示） */
    public static final String DIALOG_CONTAINER_XPATH = getElement("jobDetailPage", "dialogContainer", "//div[@class='dialog-container']");
    /** 招聘者姓名的 XPath，用于获取右侧详情页面中的招聘者姓名 */
    public static final String RECRUITER_NAME_IN_DETAIL_XPATH = getElement("jobDetailPage", "recruiterNameInDetail", "//*[@id=\"wrap\"]/div[2]/div[3]/div/div/div[2]/div[1]/div[2]/div[2]/div[2]/h2/span");
    /** 招聘者状态的 XPath，用于获取右侧详情页面中的招聘者状态 */
    public static final String RECRUITER_STATUS_XPATH = getElement("jobDetailPage", "recruiterStatus", "//*[@id=\"wrap\"]/div[2]/div[3]/div/div/div[2]/div[1]/div[2]/div[2]/div[2]/h2/span");
    /** 招聘者姓名的 XPath，用于获取聊天页面中的招聘者姓名 */
    public static final String RECRUITER_NAME_XPATH = getElement("jobDetailPage", "recruiterNameInChat", "//p[@class='base-info fl']/span[@class='name']");
    /** 招聘者职位的 XPath，用于获取聊天页面中的招聘者职位 */
    public static final String RECRUITER_TITLE_XPATH = getElement("jobDetailPage", "recruiterTitle", "//p[@class='base-info fl']/span[@class='base-title']");
    /** 公司名称的 XPath，用于获取聊天页面中的公司名称 */
    public static final String COMPANY_XPATH = getElement("jobDetailPage", "companyInChat", "//p[@class='base-info fl']/span[2]");
    /** 职位名称的 XPath，用于获取聊天页面中的职位名称 */
    public static final String POSITION_NAME_XPATH = getElement("jobDetailPage", "positionName", "//a[@class='position-content']/span[@class='position-name']");
    /** 职位薪资的 XPath，用于获取聊天页面中的职位薪资 */
    public static final String POSITION_SALARY_XPATH = getElement("jobDetailPage", "positionSalary", "//a[@class='position-content']/span[@class='salary']");
    /** 职位城市的 XPath，用于获取聊天页面中的职位所在城市 */
    public static final String POSITION_CITY_XPATH = getElement("jobDetailPage", "positionCity", "//a[@class='position-content']/span[@class='city']");
    /** 文件上传输入框的 XPath，用于定位图片简历上传的输入框 */
    public static final String FILE_INPUT_XPATH = getElement("jobDetailPage", "fileInput", "//div[@aria-label='发送图片']//input[@type='file']");
    /** 聊天输入区域的 XPath，用于检测是否有初始输入区域（用于关闭弹窗） */
    public static final String INPUT_AREA_XPATH = getElement("jobDetailPage", "inputArea", "//textarea[@class='input-area']");
    /** 对话标题的 XPath，用于检测对话弹窗标题 */
    public static final String DIALOG_TITLE_XPATH = getElement("jobDetailPage", "dialogTitle", "//div[@class='dialog-title']");
    /** 关闭图标的 XPath，用于定位对话弹窗中的关闭按钮 */
    public static final String CLOSE_ICON_XPATH = getElement("jobDetailPage", "closeIcon", "//i[@class='icon-close']");
    /** 对话限制提示的 CSS 选择器，用于检测是否达到沟通上限 */
    public static final String DIALOG_CON_CSS = getElement("jobDetailPage", "dialogCon", "dialog-con");

    // 聊天页面相关元素 (https://www.zhipin.com/web/geek/chat)
    /** 聊天列表项的 XPath，用于定位聊天页面中的每个聊天记录 */
    public static final String CHAT_LIST_ITEM_XPATH = getElement("chatPage", "chatListItem", "//li[@role='listitem']");
    /** 聊天页面公司名称的 XPath，用于获取聊天记录中的公司名称 */
    public static final String COMPANY_NAME_IN_CHAT_XPATH = getElement("chatPage", "companyNameInChat", "//span[@class='name-box']//span[2]");
    /** 聊天页面消息内容的 XPath，用于获取聊天记录中的最后一条消息 */
    public static final String MESSAGE_IN_CHAT_XPATH = getElement("chatPage", "messageInChat", "//span[@class='last-msg-text']");
    /** 滚动加载更多的 XPath，用于定位“滚动加载更多”提示 */
    public static final String SCROLL_LOAD_XPATH = getElement("chatPage", "scrollLoad", "//div[contains(text(), '滚动加载更多')]");
    /** 加载完成提示的 XPath，用于检测是否已加载所有聊天记录（“没有更多了”） */
    public static final String FINISHED_XPATH = getElement("chatPage", "finished", "//div[@class='finished']");
}