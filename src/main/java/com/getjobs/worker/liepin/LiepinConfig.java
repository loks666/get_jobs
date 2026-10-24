package com.getjobs.worker.liepin;

import lombok.Data;

import java.util.List;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Data
public class LiepinConfig {
    /**
     * 搜索关键词列表
     */
    private List<String> keywords;

    /**
     * 城市编码
     */
    private String cityCode;

    /**
     * 薪资范围
     */
    private String salary;

    /**
     * 招聘者活跃（pubTime 代码值，空表示不限）
     */
    private String pubTime;

    /**
     * 每批投递份数（0=不限）
     */
    private Integer batchSize;

    /**
     * 每次投递间隔随机范围（秒）
     */
    private Integer applyIntervalMin;
    private Integer applyIntervalMax;

    /**
     * 批次完成后等待随机范围（秒）
     */
    private Integer batchWaitMin;
    private Integer batchWaitMax;

    /**
     * 批次完成后动作：continue=等待后继续，stop=停止任务
     */
    private String batchAction;

}
