package com.getjobs.application.service;

import com.getjobs.application.entity.BossIndustryEntity;
import com.getjobs.application.mapper.BossIndustryMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BossIndustryService {

    private final BossIndustryMapper bossIndustryMapper;

    public BossIndustryService(BossIndustryMapper bossIndustryMapper) {
        this.bossIndustryMapper = bossIndustryMapper;
    }

    /**
     * 获取所有行业
     */
    public List<BossIndustryEntity> getAllIndustries() {
        return bossIndustryMapper.selectList(null);
    }

    /**
     * 根据代码获取行业
     */
    public BossIndustryEntity getIndustryByCode(Integer code) {
        return bossIndustryMapper.selectById(code);
    }
}
