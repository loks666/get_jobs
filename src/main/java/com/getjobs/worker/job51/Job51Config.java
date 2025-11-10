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


    // 注意：已改为在 Job51JobService 中通过 ConfigService 构建配置
    // 保留空的 init 以兼容旧调用，但建议不要再使用
    @SneakyThrows
    public static Job51Config init() {
        throw new UnsupportedOperationException("请在 Job51JobService 中通过 ConfigService 构建配置");
    }

}
