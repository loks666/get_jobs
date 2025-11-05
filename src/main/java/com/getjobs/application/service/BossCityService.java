package com.getjobs.application.service;

import com.getjobs.application.entity.BossCityEntity;
import com.getjobs.application.mapper.BossCityMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BossCityService {

    private final BossCityMapper bossCityMapper;

    public BossCityService(BossCityMapper bossCityMapper) {
        this.bossCityMapper = bossCityMapper;
    }

    /**
     * 获取所有城市
     */
    public List<BossCityEntity> getAllCities() {
        return bossCityMapper.selectList(null);
    }

    /**
     * 根据代码获取城市
     */
    public BossCityEntity getCityByCode(Integer code) {
        return bossCityMapper.selectById(code);
    }
}
