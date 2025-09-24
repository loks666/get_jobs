package getjobs.modules.job51.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.util.List;

/**
 * 51Job API响应数据结构
 * 
 * @author getjobs
 * @since v2.1.1
 */
@Data
public class Job51ApiResponse {

    /**
     * 响应状态 (1: 成功)
     */
    @JsonProperty("status")
    private String status;

    /**
     * 响应消息
     */
    @JsonProperty("message")
    private String message;

    /**
     * 响应数据体
     */
    @JsonProperty("resultbody")
    private Job51ResultBody resultbody;

    @Data
    public static class Job51ResultBody {
        
        /**
         * 搜索类型
         */
        @JsonProperty("searchType")
        private Integer searchType;

        /**
         * 引擎关键词类型
         */
        @JsonProperty("engineKeywordType")
        private Integer engineKeywordType;

        /**
         * 请求ID
         */
        @JsonProperty("requestId")
        private String requestId;

        /**
         * 职位数据
         */
        @JsonProperty("job")
        private Job51JobData job;
    }

    @Data
    public static class Job51JobData {
        
        /**
         * 职位列表
         */
        @JsonProperty("items")
        private List<Job51JobItem> items;

        /**
         * 总数量
         */
        @JsonProperty("totalCount")
        private Integer totalCount;

        /**
         * 请求ID
         */
        @JsonProperty("requestId")
        private String requestId;

        /**
         * 策略ID
         */
        @JsonProperty("policyId")
        private String policyId;

        /**
         * 策略类型
         */
        @JsonProperty("policyType")
        private String policyType;

        /**
         * 策略
         */
        @JsonProperty("policies")
        private String policies;

        /**
         * 总数量（备用字段）
         */
        @JsonProperty("totalcount")
        private Integer totalcount;
    }

    @Data
    public static class Job51JobItem {
        
        /**
         * 属性信息
         */
        @JsonProperty("property")
        @JsonDeserialize(using = Job51PropertyDeserializer.class)
        private Job51Property property;

        /**
         * 职位ID
         */
        @JsonProperty("jobId")
        private String jobId;

        /**
         * 职位类型
         */
        @JsonProperty("jobType")
        private String jobType;

        /**
         * 职位名称
         */
        @JsonProperty("jobName")
        private String jobName;

        /**
         * 职位标签
         */
        @JsonProperty("jobTags")
        private List<String> jobTags;

        /**
         * 职位数量字符串
         */
        @JsonProperty("jobNumString")
        private String jobNumString;

        /**
         * 工作区域代码
         */
        @JsonProperty("workAreaCode")
        private String workAreaCode;

        /**
         * 职位区域代码
         */
        @JsonProperty("jobAreaCode")
        private String jobAreaCode;

        /**
         * 职位区域字符串
         */
        @JsonProperty("jobAreaString")
        private String jobAreaString;

        /**
         * 区域拼音
         */
        @JsonProperty("hrefAreaPinYin")
        private String hrefAreaPinYin;

        /**
         * 职位区域级别详情
         */
        @JsonProperty("jobAreaLevelDetail")
        private Job51AreaDetail jobAreaLevelDetail;

        /**
         * 薪资字符串
         */
        @JsonProperty("provideSalaryString")
        private String provideSalaryString;

        /**
         * 发布日期字符串
         */
        @JsonProperty("issueDateString")
        private String issueDateString;

        /**
         * 确认日期字符串
         */
        @JsonProperty("confirmDateString")
        private String confirmDateString;

        /**
         * 工作年限
         */
        @JsonProperty("workYear")
        private String workYear;

        /**
         * 工作年限字符串
         */
        @JsonProperty("workYearString")
        private String workYearString;

        /**
         * 学历字符串
         */
        @JsonProperty("degreeString")
        private String degreeString;

        /**
         * 行业类型1
         */
        @JsonProperty("industryType1")
        private String industryType1;

        /**
         * 行业类型2
         */
        @JsonProperty("industryType2")
        private String industryType2;

        /**
         * 行业类型1字符串
         */
        @JsonProperty("industryType1Str")
        private String industryType1Str;

        /**
         * 行业类型2字符串
         */
        @JsonProperty("industryType2Str")
        private String industryType2Str;

        /**
         * 功能类型1代码
         */
        @JsonProperty("funcType1Code")
        private String funcType1Code;

        /**
         * 功能类型2代码
         */
        @JsonProperty("funcType2Code")
        private String funcType2Code;

        /**
         * 专业1字符串
         */
        @JsonProperty("major1Str")
        private String major1Str;

        /**
         * 专业2字符串
         */
        @JsonProperty("major2Str")
        private String major2Str;

        /**
         * 加密公司ID
         */
        @JsonProperty("encCoId")
        private String encCoId;

        /**
         * 公司名称
         */
        @JsonProperty("companyName")
        private String companyName;

        /**
         * 完整公司名称
         */
        @JsonProperty("fullCompanyName")
        private String fullCompanyName;

        /**
         * 公司Logo
         */
        @JsonProperty("companyLogo")
        private String companyLogo;

        /**
         * 公司类型字符串
         */
        @JsonProperty("companyTypeString")
        private String companyTypeString;

        /**
         * 公司规模字符串
         */
        @JsonProperty("companySizeString")
        private String companySizeString;

        /**
         * 公司规模代码
         */
        @JsonProperty("companySizeCode")
        private String companySizeCode;

        /**
         * 公司行业类型1字符串
         */
        @JsonProperty("companyIndustryType1Str")
        private String companyIndustryType1Str;

        /**
         * 公司行业类型2字符串
         */
        @JsonProperty("companyIndustryType2Str")
        private String companyIndustryType2Str;

        /**
         * HR用户ID
         */
        @JsonProperty("hrUid")
        private String hrUid;

        /**
         * HR姓名
         */
        @JsonProperty("hrName")
        private String hrName;

        /**
         * HR小头像URL
         */
        @JsonProperty("smallHrLogoUrl")
        private String smallHrLogoUrl;

        /**
         * HR职位
         */
        @JsonProperty("hrPosition")
        private String hrPosition;

        /**
         * HR活跃状态（绿色）
         */
        @JsonProperty("hrActiveStatusGreen")
        private String hrActiveStatusGreen;

        /**
         * HR勋章标题
         */
        @JsonProperty("hrMedalTitle")
        private String hrMedalTitle;

        /**
         * HR勋章等级
         */
        @JsonProperty("hrMedalLevel")
        private String hrMedalLevel;

        /**
         * 是否显示HR勋章标题
         */
        @JsonProperty("showHrMedalTitle")
        private Boolean showHrMedalTitle;

        /**
         * HR是否在线
         */
        @JsonProperty("hrIsOnline")
        private Boolean hrIsOnline;

        /**
         * 是否在线
         */
        @JsonProperty("isOnline")
        private Boolean isOnline;

        /**
         * HR标签
         */
        @JsonProperty("hrLabels")
        private List<String> hrLabels;

        /**
         * 更新日期时间
         */
        @JsonProperty("updateDateTime")
        private String updateDateTime;

        /**
         * 经度
         */
        @JsonProperty("lon")
        private String lon;

        /**
         * 纬度
         */
        @JsonProperty("lat")
        private String lat;

        /**
         * 是否沟通过
         */
        @JsonProperty("isCommunicate")
        private Boolean isCommunicate;

        /**
         * 是否来自学友汇
         */
        @JsonProperty("isFromXyx")
        private Boolean isFromXyx;

        /**
         * 是否实习
         */
        @JsonProperty("isIntern")
        private Boolean isIntern;

        /**
         * 是否模范雇主
         */
        @JsonProperty("isModelEmployer")
        private Boolean isModelEmployer;

        /**
         * 是否快速反馈
         */
        @JsonProperty("isQuickFeedback")
        private Boolean isQuickFeedback;

        /**
         * 是否推广
         */
        @JsonProperty("isPromotion")
        private Boolean isPromotion;

        /**
         * 是否申请
         */
        @JsonProperty("isApply")
        private Boolean isApply;

        /**
         * 是否过期
         */
        @JsonProperty("isExpire")
        private Boolean isExpire;

        /**
         * 职位链接
         */
        @JsonProperty("jobHref")
        private String jobHref;

        /**
         * 职位描述
         */
        @JsonProperty("jobDescribe")
        private String jobDescribe;

        /**
         * 公司链接
         */
        @JsonProperty("companyHref")
        private String companyHref;

        /**
         * 是否允许在线聊天
         */
        @JsonProperty("allowChatOnline")
        private Boolean allowChatOnline;

        /**
         * CTM ID
         */
        @JsonProperty("ctmId")
        private Long ctmId;

        /**
         * 任期
         */
        @JsonProperty("term")
        private String term;

        /**
         * 任期字符串
         */
        @JsonProperty("termStr")
        private String termStr;

        /**
         * 地标ID
         */
        @JsonProperty("landmarkId")
        private String landmarkId;

        /**
         * 地标字符串
         */
        @JsonProperty("landmarkString")
        private String landmarkString;

        /**
         * 检索器名称
         */
        @JsonProperty("retrieverName")
        private String retrieverName;

        /**
         * 扩展信息02
         */
        @JsonProperty("exrInfo02")
        @JsonDeserialize(using = Job51ExrInfo02Deserializer.class)
        private Job51ExrInfo02 exrInfo02;

        /**
         * HR信息类型
         */
        @JsonProperty("hrInfoType")
        private Integer hrInfoType;

        /**
         * 是否远程工作
         */
        @JsonProperty("isRemoteWork")
        private Boolean isRemoteWork;

        /**
         * 是否允许联系
         */
        @JsonProperty("contactAllowed")
        private String contactAllowed;

        /**
         * 联系日期
         */
        @JsonProperty("contactDay")
        private String contactDay;

        /**
         * 联系时间
         */
        @JsonProperty("contactTime")
        private String contactTime;

        /**
         * 是否有HR手机号
         */
        @JsonProperty("hasHrMobile")
        private Boolean hasHrMobile;

        /**
         * 职位标签排序
         */
        @JsonProperty("jobTagsForOrder")
        private List<String> jobTagsForOrder;

        /**
         * 职位标签列表
         */
        @JsonProperty("jobTagsList")
        private List<Job51JobTag> jobTagsList;

        /**
         * 是否允许聊天
         */
        @JsonProperty("isAllowChat")
        private Boolean isAllowChat;

        /**
         * 芝麻标签列表
         */
        @JsonProperty("sesameLabelList")
        private List<Job51SesameLabel> sesameLabelList;

        /**
         * 职位福利代码数据列表
         */
        @JsonProperty("jobWelfareCodeDataList")
        private List<Job51WelfareData> jobWelfareCodeDataList;

        /**
         * 职位最高薪资
         */
        @JsonProperty("jobSalaryMax")
        private String jobSalaryMax;

        /**
         * 职位最低薪资
         */
        @JsonProperty("jobSalaryMin")
        private String jobSalaryMin;

        /**
         * 是否转发职位
         */
        @JsonProperty("isReprintJob")
        private String isReprintJob;

        /**
         * 申请时间文本
         */
        @JsonProperty("applyTimeText")
        private String applyTimeText;

        /**
         * 触发批量投递
         */
        @JsonProperty("triggerBatchDeliver")
        private Boolean triggerBatchDeliver;

        /**
         * 职位发布类型
         */
        @JsonProperty("jobReleaseType")
        private String jobReleaseType;

        /**
         * 在线HR标签列表
         */
        @JsonProperty("onlineHrLabelList")
        private List<String> onlineHrLabelList;

        /**
         * 显示类型URL
         */
        @JsonProperty("showTypeUrl")
        private String showTypeUrl;

        /**
         * 职位方案
         */
        @JsonProperty("jobScheme")
        private String jobScheme;

        /**
         * 公司ID
         */
        @JsonProperty("coId")
        private String coId;
    }

    @Data
    public static class Job51AreaDetail {
        
        /**
         * 省份代码
         */
        @JsonProperty("provinceCode")
        private String provinceCode;

        /**
         * 省份字符串
         */
        @JsonProperty("provinceString")
        private String provinceString;

        /**
         * 城市代码
         */
        @JsonProperty("cityCode")
        private String cityCode;

        /**
         * 城市字符串
         */
        @JsonProperty("cityString")
        private String cityString;

        /**
         * 区域字符串
         */
        @JsonProperty("districtString")
        private String districtString;

        /**
         * 地标字符串
         */
        @JsonProperty("landMarkString")
        private String landMarkString;
    }

    @Data
    public static class Job51JobTag {
        
        /**
         * 职位标签名称
         */
        @JsonProperty("jobTagName")
        private String jobTagName;
    }

    @Data
    public static class Job51SesameLabel {
        
        /**
         * 标签名称
         */
        @JsonProperty("labelName")
        private String labelName;

        /**
         * 标签翻译名称
         */
        @JsonProperty("labelTranslateName")
        private String labelTranslateName;

        /**
         * 标签代码
         */
        @JsonProperty("labelCode")
        private String labelCode;

        /**
         * 标签定义
         */
        @JsonProperty("labelDefinition")
        private String labelDefinition;
    }

    @Data
    public static class Job51WelfareData {
        
        /**
         * 代码
         */
        @JsonProperty("code")
        private String code;

        /**
         * 中文标题
         */
        @JsonProperty("chineseTitle")
        private String chineseTitle;

        /**
         * 英文标题
         */
        @JsonProperty("englishTitle")
        private String englishTitle;

        /**
         * 类型代码
         */
        @JsonProperty("typeCode")
        private String typeCode;

        /**
         * 类型标题
         */
        @JsonProperty("typeTitle")
        private String typeTitle;
    }

    @Data
    public static class Job51Property {
        
        /**
         * 是否主动
         */
        @JsonProperty("isInitiative")
        private String isInitiative;

        /**
         * 页面代码
         */
        @JsonProperty("pageCode")
        private String pageCode;

        /**
         * 短页面代码
         */
        @JsonProperty("shortPageCode")
        private String shortPageCode;

        /**
         * 搜索类型
         */
        @JsonProperty("searchType")
        private String searchType;

        /**
         * 职位排名
         */
        @JsonProperty("jobRank")
        private String jobRank;

        /**
         * 策略ID
         */
        @JsonProperty("policyId")
        private Object policyId;

        /**
         * 关键词
         */
        @JsonProperty("keyword")
        private String keyword;

        /**
         * 页面编号
         */
        @JsonProperty("pageNum")
        private String pageNum;

        /**
         * 请求ID
         */
        @JsonProperty("requestId")
        private String requestId;

        /**
         * 职位类型
         */
        @JsonProperty("jobType")
        private String jobType;
    }

    @Data
    public static class Job51ExrInfo02 {
        
        /**
         * 检索器名称
         */
        @JsonProperty("retrieverName")
        private String retrieverName;

        /**
         * 参考职位ID
         */
        @JsonProperty("referJobId")
        private String referJobId;

        /**
         * 意图
         */
        @JsonProperty("intentions")
        private String intentions;

        /**
         * 广告扩展功能
         */
        @JsonProperty("adExtendFunc")
        private String adExtendFunc;

        /**
         * 广告扩展城市
         */
        @JsonProperty("adExtendCity")
        private String adExtendCity;

        /**
         * 工作功能混合标签结果扩展信息
         */
        @JsonProperty("workFuncMixedLabelResultExrInfo")
        private String workFuncMixedLabelResultExrInfo;
    }
}
