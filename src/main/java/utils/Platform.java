package utils;

import lombok.Getter;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Getter
public enum Platform {
    ZHILIAN("智联招聘"),
    BOSS("Boss直聘"),
    LIEPIN("猎聘"),
    JOB51("前程无忧"),
    LAGOU("拉勾网"),
    UNKNOWN("未知平台");

    // 获取枚举值的描述
    private final String platformName;

    // 构造函数
    Platform(String platformName) {
        this.platformName = platformName;
    }

}

