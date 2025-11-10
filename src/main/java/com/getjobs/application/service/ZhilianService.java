package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.getjobs.application.entity.ZhilianConfigEntity;
import com.getjobs.application.mapper.ZhilianConfigMapper;
import com.getjobs.worker.zhilian.ZhilianConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ZhilianService {
    private final ZhilianConfigMapper zhilianConfigMapper;

    /** 获取第一条配置（通常只有一条） */
    public ZhilianConfigEntity getFirstConfig() {
        QueryWrapper<ZhilianConfigEntity> wrapper = new QueryWrapper<>();
        wrapper.last("LIMIT 1");
        return zhilianConfigMapper.selectOne(wrapper);
    }

    /** 从专表构建 ZhilianConfig */
    public ZhilianConfig loadZhilianConfig() {
        ZhilianConfigEntity entity = getFirstConfig();
        ZhilianConfig config = new ZhilianConfig();
        if (entity == null) {
            log.warn("zhilian_config 表为空，使用默认空配置");
            config.setKeywords(new ArrayList<>());
            config.setCityCode("0");
            config.setSalary("0");
            return config;
        }

        // 关键词解析：支持逗号或括号列表
        config.setKeywords(parseListString(entity.getKeywords()));

        // 城市：中文名映射到代码；缺省或“不限”映射为 0
        String city = safeTrim(entity.getCityCode());
        if (city == null || city.isEmpty() || "不限".equals(city)) {
            config.setCityCode("0");
        } else {
            String code = com.getjobs.worker.zhilian.ZhilianEnum.CityCode.forValue(city).getCode();
            config.setCityCode(code);
        }

        // 薪资：缺省或“不限”映射为 0，其它保持原值
        String salary = safeTrim(entity.getSalary());
        if (salary == null || salary.isEmpty() || "不限".equals(salary)) {
            config.setSalary("0");
        } else {
            config.setSalary(salary);
        }
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

    private String safeTrim(String s) { return s == null ? null : s.trim(); }
}