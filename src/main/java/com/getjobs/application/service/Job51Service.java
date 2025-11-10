package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.getjobs.application.entity.Job51ConfigEntity;
import com.getjobs.application.mapper.Job51ConfigMapper;
import com.getjobs.worker.job51.Job51Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class Job51Service {
    private final Job51ConfigMapper job51ConfigMapper;

    /** 获取第一条配置（通常只有一条） */
    public Job51ConfigEntity getFirstConfig() {
        QueryWrapper<Job51ConfigEntity> wrapper = new QueryWrapper<>();
        wrapper.last("LIMIT 1");
        return job51ConfigMapper.selectOne(wrapper);
    }

    /** 从专表构建 Job51Config */
    public Job51Config loadJob51Config() {
        Job51ConfigEntity entity = getFirstConfig();
        Job51Config config = new Job51Config();
        if (entity == null) {
            log.warn("job51_config 表为空，使用默认空配置");
            config.setKeywords(new ArrayList<>());
            config.setJobArea(new ArrayList<>());
            config.setSalary(new ArrayList<>());
            return config;
        }

        // 关键词解析
        config.setKeywords(parseListString(entity.getKeywords()));

        // 城市区域：中文名或代码列表 -> 统一为代码列表
        List<String> areaNames = parseListString(entity.getJobArea());
        List<String> areaCodes = new ArrayList<>();
        for (String name : areaNames) {
            if (name == null || name.isEmpty()) continue;
            String code = com.getjobs.worker.job51.Job51Enum.jobArea.forValue(name).getCode();
            areaCodes.add(code);
        }
        config.setJobArea(areaCodes);

        // 薪资范围：中文名或代码列表 -> 统一为代码列表
        List<String> salaryNames = parseListString(entity.getSalary());
        List<String> salaryCodes = new ArrayList<>();
        for (String name : salaryNames) {
            if (name == null || name.isEmpty()) continue;
            String code = com.getjobs.worker.job51.Job51Enum.Salary.forValue(name).getCode();
            salaryCodes.add(code);
        }
        config.setSalary(salaryCodes);

        return config;
    }

    public List<String> parseListString(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new ArrayList<>();
        String s = raw.trim().replace('，', ',');
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1);
        if (s.trim().isEmpty()) return new ArrayList<>();
        return java.util.Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(str -> !str.isEmpty())
                .collect(Collectors.toList());
    }
}