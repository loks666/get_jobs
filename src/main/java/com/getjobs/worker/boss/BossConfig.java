package com.getjobs.worker.boss;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 *
 * Boss配置数据类
 * 配置加载由 BossConfigLoaderService 负责
 */
@Data
public class BossConfig {
    public static final String DEFAULT_REPEAT_CHAT_MESSAGES = String.join("\n",
            "请问是有什么顾虑需要沟通吗？",
            "我可以去贵公司面试吗？",
            "请问贵公司还招人吗？",
            "对于这个岗位有什么技术需求呢？",
            "我可以把我的简历发给您看看吗？",
            "能问一下贵公司办公地点在哪里吗？需不需要经常出差");

    /** 每行一条跟进消息；空列表时跳过，不回退到首次打招呼语。 */
    private String repeatChatMessages = DEFAULT_REPEAT_CHAT_MESSAGES;

    public String randomRepeatChatMessage() {
        List<String> messages = repeatChatMessages == null ? List.of()
                : repeatChatMessages.lines().map(String::strip).filter(s -> !s.isEmpty()).toList();
        return messages.isEmpty() ? null
                : messages.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(messages.size()));
    }
    /**
     * 用于打招呼的语句
     */
    private String sayHi;

    /**
     * 开发者模式
     */
    private Boolean debugger;

    /**
     * 搜索关键词列表
     */
    private List<String> keywords;

    /**
     * 城市编码
     */
    private List<String> cityCode;

    /**
     * 自定义城市编码映射
     */
    private Map<String, String> customCityCode;

    /**
     * 行业列表
     */
    private List<String> industry;

    /**
     * 工作经验要求
     */
    private List<String> experience;

    /**
     * 工作类型
     */
    private String jobType;

    /**
     * 薪资范围（多选）
     */
    private java.util.List<String> salary;

    /**
     * 学历要求列表
     */
    private List<String> degree;

    /**
     * 公司规模列表
     */
    private List<String> scale;

    /**
     * 公司融资阶段列表
     */
    private List<String> stage;

    /**
     * 是否开放AI检测
     */
    private Boolean enableAI;

    /**
     * 是否过滤不活跃hr
     */
    private Boolean filterDeadHR;

    /**
     * 是否发送图片简历
     */
    private Boolean sendImgResume;
    /** 允许对“继续沟通”的Boss再次发送消息，默认关闭。 */
    private Boolean allowRepeatChat = false;

    /**
     * 目标薪资
     */
    private List<Integer> expectedSalary;

    /**
     * 等待时间
     */
    private String waitTime;

    /**
     * HR未上线状态
     */
    private List<String> deadStatus;
}
