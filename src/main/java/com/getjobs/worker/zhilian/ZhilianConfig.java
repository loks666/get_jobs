package com.getjobs.worker.zhilian;

import com.getjobs.worker.utils.JobUtils;
import lombok.Data;
import lombok.SneakyThrows;

import java.util.List;
import java.util.Objects;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Data
public class ZhilianConfig {
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


    @SneakyThrows
    public static ZhilianConfig init() {
        ZhilianConfig config = JobUtils.getConfig(ZhilianConfig.class);
        // 转换城市编码
        config.setCityCode(ZhilianEnum.CityCode.forValue(config.getCityCode()).getCode());
        String salary = config.getSalary();
        config.setSalary(Objects.equals("不限", salary) ? "0" : salary);
        return config;
    }

}
