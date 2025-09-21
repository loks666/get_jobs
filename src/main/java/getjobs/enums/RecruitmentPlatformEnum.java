package getjobs.enums;

import lombok.Getter;

/**
 * 招聘平台枚举
 * 
 * @author loks666
 *         项目链接: <a href=
 *         "https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Getter
public enum RecruitmentPlatformEnum {
    BOSS_ZHIPIN("Boss直聘", "boss", "https://www.zhipin.com"),
    ZHILIAN_ZHAOPIN("智联招聘", "zhilian", "https://www.zhaopin.com"),
    JOB_51("51job", "51job", "https://51job.com/");

    /**
     * 平台名称
     */
    private final String platformName;

    /**
     * 平台代码
     */
    private final String platformCode;

    /**
     * 平台主页URL
     */
    private final String homeUrl;

    RecruitmentPlatformEnum(String platformName, String platformCode, String homeUrl) {
        this.platformName = platformName;
        this.platformCode = platformCode;
        this.homeUrl = homeUrl;
    }

    /**
     * 根据平台代码获取枚举
     * 
     * @param platformCode 平台代码
     * @return 招聘平台枚举
     */
    public static RecruitmentPlatformEnum getByCode(String platformCode) {
        for (RecruitmentPlatformEnum platform : values()) {
            if (platform.getPlatformCode().equals(platformCode)) {
                return platform;
            }
        }
        return null;
    }

    /**
     * 根据平台名称获取枚举
     * 
     * @param platformName 平台名称
     * @return 招聘平台枚举
     */
    public static RecruitmentPlatformEnum getByName(String platformName) {
        for (RecruitmentPlatformEnum platform : values()) {
            if (platform.getPlatformName().equals(platformName)) {
                return platform;
            }
        }
        return null;
    }
}
