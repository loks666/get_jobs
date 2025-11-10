package com.getjobs.worker.liepin;

/**
 * 猎聘网页元素定位器
 * 集中管理所有页面元素的定位表达式
 */
public class Locators {
    // 搜索结果页相关元素（分页与弹窗）
    public static final String PAGINATION_BOX = ".list-pagination-box";
    // Ant Design v5 中下一页为 li.ant-pagination-next 内的按钮
    public static final String NEXT_PAGE = "li.ant-pagination-next";
    public static final String SUBSCRIBE_CLOSE_BTN = "//div[contains(@class, 'subscribe-close-btn')]";

    // 岗位列表容器（用于遍历卡片和定位按钮）
    public static final String JOB_CARDS = "//div[contains(@class, 'job-card-pc-container')]";

    // 聊天相关元素（用于检测聊天窗口并关闭）
    public static final String CHAT_HEADER = ".__im_basic__header-wrap";
    public static final String CHAT_CLOSE = "div.__im_basic__contacts-title svg";
}