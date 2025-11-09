package com.getjobs.worker.job51;

import com.getjobs.worker.utils.JobUtils;
import lombok.Data;
import lombok.SneakyThrows;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 * 前程无忧自动投递简历
 */
@Data
public class Job51Config {


    /**
     * 搜索关键词列表
     */
    private List<String> keywords;

    /**
     * 城市编码
     */
    private List<String> jobArea;

    /**
     * 薪资范围
     */
    private List<String> salary;


    @SneakyThrows
    public static Job51Config init() {
        Job51Config config = JobUtils.getConfig(Job51Config.class);
        // 转换城市编码
        config.setJobArea(config.getJobArea().stream().map(value -> Job51Enum.jobArea.forValue(value).getCode()).collect(Collectors.toList()));
        // 转换薪资范围
        config.setSalary(config.getSalary().stream().map(value -> Job51Enum.Salary.forValue(value).getCode()).collect(Collectors.toList()));
        return config;
    }

}
