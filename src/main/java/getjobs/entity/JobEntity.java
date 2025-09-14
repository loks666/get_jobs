package getjobs.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 职位信息实体类
 * 
 * @author getjobs
 * @since v2.0.1
 */
@Data
@Entity
@Table(name = "job_info")
@EqualsAndHashCode(callSuper = true)
public class JobEntity extends BaseEntity {

    // ==================== 基础职位信息 ====================

    /**
     * 职位标题
     */
    @Column(name = "job_title", nullable = false, length = 200)
    private String jobTitle;

    /**
     * 薪资描述
     */
    @Column(name = "salary_desc", length = 100)
    private String salaryDesc;

    /**
     * 工作经验要求
     */
    @Column(name = "job_experience", length = 50)
    private String jobExperience;

    /**
     * 学历要求
     */
    @Column(name = "job_degree", length = 50)
    private String jobDegree;

    /**
     * 职位标签（JSON数组格式）
     */
    @Column(name = "job_labels", columnDefinition = "TEXT")
    private String jobLabels;

    /**
     * 技能要求（JSON数组格式）
     */
    @Column(name = "skills", columnDefinition = "TEXT")
    private String skills;

    /**
     * 职位描述
     */
    @Column(name = "job_description", columnDefinition = "TEXT")
    private String jobDescription;

    /**
     * 职位要求
     */
    @Column(name = "job_requirements", columnDefinition = "TEXT")
    private String jobRequirements;

    /**
     * 职位链接
     */
    @Column(name = "job_url", length = 500)
    private String jobUrl;

    // ==================== 公司信息 ====================

    /**
     * 公司名称
     */
    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    /**
     * 公司行业
     */
    @Column(name = "company_industry", length = 100)
    private String companyIndustry;

    /**
     * 公司融资阶段
     */
    @Column(name = "company_stage", length = 50)
    private String companyStage;

    /**
     * 公司规模
     */
    @Column(name = "company_scale", length = 50)
    private String companyScale;

    /**
     * 公司Logo
     */
    @Column(name = "company_logo", length = 500)
    private String companyLogo;

    /**
     * 公司标签（列表以分隔符拼接）
     */
    @Column(name = "company_tag", length = 200)
    private String companyTag;

    // ==================== 工作地点信息 ====================

    /**
     * 工作城市
     */
    @Column(name = "work_city", length = 50)
    private String workCity;

    /**
     * 工作区域
     */
    @Column(name = "work_area", length = 100)
    private String workArea;

    /**
     * 商圈
     */
    @Column(name = "business_district", length = 100)
    private String businessDistrict;

    /**
     * 经度
     */
    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    /**
     * 纬度
     */
    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    // ==================== HR信息 ====================

    /**
     * HR姓名
     */
    @Column(name = "hr_name", length = 100)
    private String hrName;

    /**
     * HR职位
     */
    @Column(name = "hr_title", length = 100)
    private String hrTitle;

    /**
     * HR头像
     */
    @Column(name = "hr_avatar", length = 500)
    private String hrAvatar;

    /**
     * HR是否在线
     */
    @Column(name = "hr_online")
    private Boolean hrOnline;

    /**
     * HR认证等级
     */
    @Column(name = "hr_cert_level")
    private Integer hrCertLevel;

    /**
     * HR 活跃时间/状态原文
     */
    @Column(name = "hr_active_time", length = 50)
    private String hrActiveTime;

    // ==================== 系统信息 ====================

    /**
     * 数据来源平台
     */
    @Column(name = "platform", length = 50)
    private String platform;

    /**
     * 加密职位ID
     */
    @Column(name = "encrypt_job_id", length = 100)
    private String encryptJobId;

    /**
     * 加密HR ID
     */
    @Column(name = "encrypt_hr_id", length = 100)
    private String encryptHrId;

    /**
     * 加密公司ID
     */
    @Column(name = "encrypt_company_id", length = 100)
    private String encryptCompanyId;

    /**
     * 安全ID
     */
    @Column(name = "security_id", length = 200)
    private String securityId;

    /**
     * 职位状态 (0: 待处理, 1: 已处理, 2: 已忽略, 3: 已过滤)
     */
    @Column(name = "status", nullable = false)
    private Integer status = 0;

    /**
     * 过滤原因说明
     */
    @Column(name = "filter_reason", length = 500)
    private String filterReason;

    /**
     * 是否收藏
     */
    @Column(name = "is_favorite", nullable = false)
    private Boolean isFavorite = false;

    /**
     * 是否最优职位
     */
    @Column(name = "is_optimal")
    private Boolean isOptimal;

    /**
     * 是否代理职位
     */
    @Column(name = "is_proxy_job")
    private Boolean isProxyJob;

    /**
     * 代理类型
     */
    @Column(name = "proxy_type")
    private Integer proxyType;

    /**
     * 是否金猎手
     */
    @Column(name = "is_gold_hunter")
    private Boolean isGoldHunter;

    /**
     * 是否联系过
     */
    @Column(name = "is_contacted")
    private Boolean isContacted;

    /**
     * 是否屏蔽
     */
    @Column(name = "is_shielded")
    private Boolean isShielded;

    /**
     * 职位有效性状态
     */
    @Column(name = "job_valid_status")
    private Integer jobValidStatus;

    /**
     * 福利列表（JSON数组格式）
     */
    @Column(name = "welfare_list", columnDefinition = "TEXT")
    private String welfareList;

    /**
     * 图标标志列表（JSON数组格式）
     */
    @Column(name = "icon_flag_list", columnDefinition = "TEXT")
    private String iconFlagList;

    /**
     * 名称前图标（JSON数组格式）
     */
    @Column(name = "before_name_icons", columnDefinition = "TEXT")
    private String beforeNameIcons;

    /**
     * 名称后图标（JSON数组格式）
     */
    @Column(name = "after_name_icons", columnDefinition = "TEXT")
    private String afterNameIcons;

    /**
     * 图标文字
     */
    @Column(name = "icon_word", length = 100)
    private String iconWord;

    /**
     * 最少工作月数描述
     */
    @Column(name = "least_month_desc", length = 50)
    private String leastMonthDesc;

    /**
     * 每周工作天数描述
     */
    @Column(name = "days_per_week_desc", length = 50)
    private String daysPerWeekDesc;

    /**
     * 是否显示顶部位置
     */
    @Column(name = "show_top_position")
    private Boolean showTopPosition;

    /**
     * 是否海外职位
     */
    @Column(name = "is_outland")
    private Boolean isOutland;

    /**
     * 匿名状态
     */
    @Column(name = "anonymous_status")
    private Integer anonymousStatus;

    /**
     * 项目ID
     */
    @Column(name = "item_id")
    private Integer itemId;

    /**
     * 期望ID
     */
    @Column(name = "expect_id")
    private Long expectId;

    /**
     * 城市编码
     */
    @Column(name = "city_code")
    private Long cityCode;

    /**
     * 行业编码
     */
    @Column(name = "industry_code")
    private Long industryCode;

    /**
     * 职位类型
     */
    @Column(name = "job_type")
    private Integer jobType;

    /**
     * 是否ATS直接投递
     */
    @Column(name = "ats_direct_post")
    private Boolean atsDirectPost;

    /**
     * 搜索ID
     */
    @Column(name = "search_id", length = 100)
    private String searchId;

    // ==================== 职位明细信息 (jobInfo) ====================

    /**
     * 加密职位ID (来自jobInfo.encryptId)
     */
    @Column(name = "encrypt_job_detail_id", length = 100)
    private String encryptJobDetailId;

    /**
     * 加密用户ID (来自jobInfo.encryptUserId)
     */
    @Column(name = "encrypt_job_user_id", length = 100)
    private String encryptJobUserId;

    /**
     * 职位有效性状态 (来自jobInfo.invalidStatus)
     */
    @Column(name = "job_invalid_status")
    private Boolean jobInvalidStatus;

    /**
     * 职位类型编码 (来自jobInfo.position)
     */
    @Column(name = "job_position_code")
    private Long jobPositionCode;

    /**
     * 职位类型名称 (来自jobInfo.positionName)
     */
    @Column(name = "job_position_name", length = 100)
    private String jobPositionName;

    /**
     * 城市编码 (来自jobInfo.location)
     */
    @Column(name = "job_location_code")
    private Long jobLocationCode;

    /**
     * 城市名称 (来自jobInfo.locationName)
     */
    @Column(name = "job_location_name", length = 100)
    private String jobLocationName;

    /**
     * 城市URL (来自jobInfo.locationUrl)
     */
    @Column(name = "job_location_url", length = 200)
    private String jobLocationUrl;

    /**
     * 工作经验描述 (来自jobInfo.experienceName)
     */
    @Column(name = "job_experience_name", length = 100)
    private String jobExperienceName;

    /**
     * 学历描述 (来自jobInfo.degreeName)
     */
    @Column(name = "job_degree_name", length = 100)
    private String jobDegreeName;

    /**
     * 职位类型 (来自jobInfo.jobType)
     */
    @Column(name = "job_detail_type")
    private Integer jobDetailType;

    /**
     * 是否代理职位 (来自jobInfo.proxyJob)
     */
    @Column(name = "job_proxy_job")
    private Integer jobProxyJob;

    /**
     * 代理类型 (来自jobInfo.proxyType)
     */
    @Column(name = "job_proxy_type")
    private Integer jobProxyType;

    /**
     * 薪资类型描述 (来自jobInfo.payTypeDesc)
     */
    @Column(name = "job_pay_type_desc", length = 100)
    private String jobPayTypeDesc;

    /**
     * 职位描述 (来自jobInfo.postDescription)
     */
    @Column(name = "job_post_description", columnDefinition = "TEXT")
    private String jobPostDescription;

    /**
     * 加密地址ID (来自jobInfo.encryptAddressId)
     */
    @Column(name = "encrypt_address_id", length = 100)
    private String encryptAddressId;

    /**
     * 详细地址 (来自jobInfo.address)
     */
    @Column(name = "job_address", length = 500)
    private String jobAddress;

    /**
     * 经度 (来自jobInfo.longitude)
     */
    @Column(name = "job_longitude", precision = 10, scale = 7)
    private BigDecimal jobLongitude;

    /**
     * 纬度 (来自jobInfo.latitude)
     */
    @Column(name = "job_latitude", precision = 10, scale = 7)
    private BigDecimal jobLatitude;

    /**
     * 静态地图URL (来自jobInfo.staticMapUrl)
     */
    @Column(name = "job_static_map_url", length = 500)
    private String jobStaticMapUrl;

    /**
     * PC静态地图URL (来自jobInfo.pcStaticMapUrl)
     */
    @Column(name = "job_pc_static_map_url", length = 500)
    private String jobPcStaticMapUrl;

    /**
     * 百度静态地图URL (来自jobInfo.baiduStaticMapUrl)
     */
    @Column(name = "job_baidu_static_map_url", length = 500)
    private String jobBaiduStaticMapUrl;

    /**
     * 百度PC静态地图URL (来自jobInfo.baiduPcStaticMapUrl)
     */
    @Column(name = "job_baidu_pc_static_map_url", length = 500)
    private String jobBaiduPcStaticMapUrl;

    /**
     * 显示技能列表 (来自jobInfo.showSkills, JSON数组格式)
     */
    @Column(name = "job_show_skills", columnDefinition = "TEXT")
    private String jobShowSkills;

    /**
     * 匿名状态 (来自jobInfo.anonymous)
     */
    @Column(name = "job_anonymous")
    private Integer jobAnonymous;

    /**
     * 职位状态描述 (来自jobInfo.jobStatusDesc)
     */
    @Column(name = "job_status_desc", length = 100)
    private String jobStatusDesc;

    // ==================== Boss信息 (bossInfo) ====================

    /**
     * Boss姓名 (来自bossInfo.name)
     */
    @Column(name = "boss_name", length = 100)
    private String bossName;

    /**
     * Boss职位 (来自bossInfo.title)
     */
    @Column(name = "boss_title", length = 100)
    private String bossTitle;

    /**
     * Boss小头像 (来自bossInfo.tiny)
     */
    @Column(name = "boss_tiny", length = 500)
    private String bossTiny;

    /**
     * Boss大头像 (来自bossInfo.large)
     */
    @Column(name = "boss_large", length = 500)
    private String bossLarge;

    /**
     * Boss活跃时间描述 (来自bossInfo.activeTimeDesc)
     */
    @Column(name = "boss_active_time_desc", length = 100)
    private String bossActiveTimeDesc;

    /**
     * Boss是否在线 (来自bossInfo.bossOnline)
     */
    @Column(name = "boss_online")
    private Boolean bossOnline;

    /**
     * Boss品牌名称 (来自bossInfo.brandName)
     */
    @Column(name = "boss_brand_name", length = 200)
    private String bossBrandName;

    /**
     * Boss来源 (来自bossInfo.bossSource)
     */
    @Column(name = "boss_source")
    private Integer bossSource;

    /**
     * Boss是否认证 (来自bossInfo.certificated)
     */
    @Column(name = "boss_certificated")
    private Boolean bossCertificated;

    /**
     * Boss标签图标URL (来自bossInfo.tagIconUrl)
     */
    @Column(name = "boss_tag_icon_url", length = 500)
    private String bossTagIconUrl;

    /**
     * Boss头像贴纸URL (来自bossInfo.avatarStickerUrl)
     */
    @Column(name = "boss_avatar_sticker_url", length = 500)
    private String bossAvatarStickerUrl;

    // ==================== 品牌公司信息 (brandComInfo) ====================

    /**
     * 加密品牌ID (来自brandComInfo.encryptBrandId)
     */
    @Column(name = "encrypt_brand_id", length = 100)
    private String encryptBrandId;

    /**
     * 品牌名称 (来自brandComInfo.brandName)
     */
    @Column(name = "brand_name", length = 200)
    private String brandName;

    /**
     * 品牌Logo (来自brandComInfo.logo)
     */
    @Column(name = "brand_logo", length = 500)
    private String brandLogo;

    /**
     * 品牌阶段 (来自brandComInfo.stage)
     */
    @Column(name = "brand_stage")
    private Long brandStage;

    /**
     * 品牌阶段名称 (来自brandComInfo.stageName)
     */
    @Column(name = "brand_stage_name", length = 100)
    private String brandStageName;

    /**
     * 品牌规模 (来自brandComInfo.scale)
     */
    @Column(name = "brand_scale")
    private Long brandScale;

    /**
     * 品牌规模名称 (来自brandComInfo.scaleName)
     */
    @Column(name = "brand_scale_name", length = 100)
    private String brandScaleName;

    /**
     * 品牌行业 (来自brandComInfo.industry)
     */
    @Column(name = "brand_industry")
    private Long brandIndustry;

    /**
     * 品牌行业名称 (来自brandComInfo.industryName)
     */
    @Column(name = "brand_industry_name", length = 100)
    private String brandIndustryName;

    /**
     * 品牌介绍 (来自brandComInfo.introduce)
     */
    @Column(name = "brand_introduce", columnDefinition = "TEXT")
    private String brandIntroduce;

    /**
     * 品牌标签 (来自brandComInfo.labels, JSON数组格式)
     */
    @Column(name = "brand_labels", columnDefinition = "TEXT")
    private String brandLabels;

    /**
     * 品牌活跃时间 (来自brandComInfo.activeTime)
     */
    @Column(name = "brand_active_time")
    private Long brandActiveTime;

    /**
     * 是否显示品牌信息 (来自brandComInfo.visibleBrandInfo)
     */
    @Column(name = "visible_brand_info")
    private Boolean visibleBrandInfo;

    /**
     * 是否关注品牌 (来自brandComInfo.focusBrand)
     */
    @Column(name = "focus_brand")
    private Boolean focusBrand;

    /**
     * 客户品牌名称 (来自brandComInfo.customerBrandName)
     */
    @Column(name = "customer_brand_name", length = 200)
    private String customerBrandName;

    /**
     * 客户品牌阶段名称 (来自brandComInfo.customerBrandStageName)
     */
    @Column(name = "customer_brand_stage_name", length = 100)
    private String customerBrandStageName;
}
