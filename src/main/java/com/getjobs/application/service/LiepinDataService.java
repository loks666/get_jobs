package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.getjobs.application.entity.LiepinConfigEntity;
import com.getjobs.application.entity.LiepinOptionEntity;
import com.getjobs.application.mapper.LiepinConfigMapper;
import com.getjobs.application.mapper.LiepinOptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 猎聘数据服务
 * 统一管理所有猎聘相关的数据访问和配置加载
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LiepinDataService {

    private final LiepinConfigMapper liepinConfigMapper;
    private final LiepinOptionMapper liepinOptionMapper;

    // ==================== Config相关方法 ====================

    /**
     * 获取第一条配置记录（通常只有一条）
     */
    public LiepinConfigEntity getFirstConfig() {
        QueryWrapper<LiepinConfigEntity> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("id");
        wrapper.last("LIMIT 1");
        return liepinConfigMapper.selectOne(wrapper);
    }

    /**
     * 更新配置
     */
    public LiepinConfigEntity updateConfig(LiepinConfigEntity config) {
        config.setUpdatedAt(LocalDateTime.now());
        liepinConfigMapper.updateById(config);
        return config;
    }

    /**
     * 保存或更新第一条配置（选择性更新）
     * 如果数据库中没有记录，则插入新记录；否则更新第一条记录
     */
    public LiepinConfigEntity saveOrUpdateFirstSelective(LiepinConfigEntity config) {
        LiepinConfigEntity existing = getFirstConfig();
        if (existing == null) {
            // 不存在记录，插入新配置
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            liepinConfigMapper.insert(config);
            return config;
        }
        // 存在记录，选择性更新非null字段
        config.setId(existing.getId());
        if (config.getKeywords() != null) {
            existing.setKeywords(config.getKeywords());
        }
        if (config.getCity() != null) {
            existing.setCity(config.getCity());
        }
        if (config.getSalaryCode() != null) {
            existing.setSalaryCode(config.getSalaryCode());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        liepinConfigMapper.updateById(existing);
        return existing;
    }

    // ==================== Option相关方法 ====================

    /**
     * 根据类型获取选项列表
     */
    public List<LiepinOptionEntity> getOptionsByType(String type) {
        QueryWrapper<LiepinOptionEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("type", type);
        wrapper.orderByAsc("sort_order", "id");
        return liepinOptionMapper.selectList(wrapper);
    }

    /**
     * 根据类型和代码获取选项
     */
    public LiepinOptionEntity getOptionByTypeAndCode(String type, String code) {
        QueryWrapper<LiepinOptionEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("type", type);
        wrapper.eq("code", code);
        return liepinOptionMapper.selectOne(wrapper);
    }

    /**
     * 根据类型和名称获取代码
     */
    public String getCodeByTypeAndName(String type, String name) {
        QueryWrapper<LiepinOptionEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("type", type);
        wrapper.eq("name", name);
        LiepinOptionEntity entity = liepinOptionMapper.selectOne(wrapper);
        return entity != null ? entity.getCode() : "";
    }

    /**
     * 根据类型和代码获取名称
     */
    public String getNameByTypeAndCode(String type, String code) {
        LiepinOptionEntity entity = getOptionByTypeAndCode(type, code);
        return entity != null ? entity.getName() : code;
    }

    /**
     * 规范化城市为名称
     * 如果传入的是代码，则查找对应的名称；否则直接返回
     */
    public String normalizeCityToName(String cityCodeOrName) {
        if (cityCodeOrName == null || cityCodeOrName.isEmpty()) {
            return "";
        }
        // 尝试作为代码查找
        LiepinOptionEntity entity = getOptionByTypeAndCode("city", cityCodeOrName);
        if (entity != null) {
            return entity.getName();
        }
        // 如果找不到，直接返回原值（可能是手动输入的城市名）
        return cityCodeOrName;
    }
}
