package getjobs.modules.boss.dto;

import getjobs.enums.RecruitmentPlatformEnum;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 职位数据传输对象
 * 
 * @author loks666
 *         项目链接: <a href=
 *         "https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Data
public class JobDTO implements Serializable {

    // ==================== 基础职位信息 ====================

    /**
     * 岗位链接
     */
    private String href;

    /**
     * 岗位名称
     */
    private String jobName;

    /**
     * 岗位地区
     */
    private String jobArea;

    /**
     * 岗位信息
     */
    private String jobInfo;

    /**
     * 岗位薪水
     */
    private String salary;

    /**
     * 薪资描述
     */
    private String salaryDesc;

    /**
     * 工作经验要求
     */
    private String jobExperience;

    /**
     * 学历要求
     */
    private String jobDegree;

    /**
     * 职位标签
     */
    private List<String> jobLabels;

    /**
     * 技能要求
     */
    private List<String> skills;

    /**
     * 职位描述
     */
    private String jobDescription;

    /**
     * 职位要求
     */
    private String jobRequirements;

    // ==================== 公司信息 ====================

    /**
     * 公司名字
     */
    private String companyName;

    /**
     * 公司信息
     */
    private String companyInfo;

    /**
     * 公司行业
     */
    private String companyIndustry;

    /**
     * 公司融资阶段
     */
    private String companyStage;

    /**
     * 公司规模
     */
    private String companyScale;

    /**
     * 公司Logo
     */
    private String companyLogo;

    /**
     * 公司标签
     */
    private String companyTag;

    // ==================== 工作地点信息 ====================

    /**
     * 工作城市
     */
    private String workCity;

    /**
     * 工作区域
     */
    private String workArea;

    /**
     * 商圈
     */
    private String businessDistrict;

    /**
     * 经度
     */
    private BigDecimal longitude;

    /**
     * 纬度
     */
    private BigDecimal latitude;

    // ==================== HR信息 ====================

    /**
     * HR名称
     */
    private String recruiter;

    /**
     * HR姓名
     */
    private String hrName;

    /**
     * HR职位
     */
    private String hrTitle;

    /**
     * HR头像
     */
    private String hrAvatar;

    /**
     * HR是否在线
     */
    private Boolean hrOnline;

    /**
     * HR认证等级
     */
    private Integer hrCertLevel;

    /**
     * HR 活跃时间/状态原文
     */
    private String hrActiveTime;

    // ==================== 系统信息 ====================

    /**
     * 数据来源平台
     */
    private String platform;

    /**
     * 加密职位ID
     */
    private String encryptJobId;

    /**
     * 加密HR ID
     */
    private String encryptHrId;

    /**
     * 加密公司ID
     */
    private String encryptCompanyId;

    /**
     * 安全ID
     */
    private String securityId;

    /**
     * 职位状态 (0: 待处理, 1: 已处理, 2: 已忽略, 3: 已过滤)
     */
    private Integer status = 0;

    /**
     * 过滤原因说明
     */
    private String filterReason;

    /**
     * 是否收藏
     */
    private Boolean isFavorite = false;

    /**
     * 是否最优职位
     */
    private Boolean isOptimal;

    /**
     * 是否代理职位
     */
    private Boolean isProxyJob;

    /**
     * 代理类型
     */
    private Integer proxyType;

    /**
     * 是否金猎手
     */
    private Boolean isGoldHunter;

    /**
     * 是否联系过
     */
    private Boolean isContacted;

    /**
     * 是否屏蔽
     */
    private Boolean isShielded;

    /**
     * 职位有效性状态
     */
    private Integer jobValidStatus;

    /**
     * 福利列表
     */
    private List<String> welfareList;

    /**
     * 图标标志列表
     */
    private List<Integer> iconFlagList;

    /**
     * 名称前图标
     */
    private List<String> beforeNameIcons;

    /**
     * 名称后图标
     */
    private List<String> afterNameIcons;

    /**
     * 图标文字
     */
    private String iconWord;

    /**
     * 最少工作月数描述
     */
    private String leastMonthDesc;

    /**
     * 每周工作天数描述
     */
    private String daysPerWeekDesc;

    /**
     * 是否显示顶部位置
     */
    private Boolean showTopPosition;

    /**
     * 是否海外职位
     */
    private Boolean isOutland;

    /**
     * 匿名状态
     */
    private Integer anonymousStatus;

    /**
     * 项目ID
     */
    private Integer itemId;

    /**
     * 期望ID
     */
    private Long expectId;

    /**
     * 城市编码
     */
    private Long cityCode;

    /**
     * 行业编码
     */
    private Long industryCode;

    /**
     * 职位类型
     */
    private Integer jobType;

    /**
     * 是否ATS直接投递
     */
    private Boolean atsDirectPost;

    /**
     * 搜索ID
     */
    private String searchId;

    @Override
    public String toString() {
        return String.format("【%s, %s, %s, %s, %s, %s】", companyName, jobName, jobArea, salary, companyTag, recruiter);
    }

    public String toString(RecruitmentPlatformEnum platform) {
        if (platform == RecruitmentPlatformEnum.ZHILIAN_ZHAOPIN) {
            return String.format("【%s, %s, %s, %s, %s, %s, %s】", companyName, jobName, jobArea, companyTag, salary,
                    recruiter, href);
        }
        if (platform == RecruitmentPlatformEnum.BOSS_ZHIPIN) {
            return String.format("【%s, %s, %s, %s, %s, %s】", companyName, jobName, jobArea, salary, companyTag,
                    recruiter);
        }
        return String.format("【%s, %s, %s, %s, %s, %s, %s】", companyName, jobName, jobArea, salary, companyTag,
                recruiter, href);
    }
}
