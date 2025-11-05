package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.getjobs.application.entity.AiEntity;
import com.getjobs.application.mapper.AiMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI配置服务类
 */
@Service
public class AiConfigService {

    private static final Logger log = LoggerFactory.getLogger(AiConfigService.class);

    @Autowired
    private AiMapper aiMapper;

    /**
     * 获取AI配置（获取第一条记录，如果不存在则创建默认配置）
     * @return AI配置
     */
    public AiEntity getAiConfig() {
        LambdaQueryWrapper<AiEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(AiEntity::getId).last("LIMIT 1");

        AiEntity aiEntity = aiMapper.selectOne(queryWrapper);

        // 如果不存在，创建默认配置
        if (aiEntity == null) {
            aiEntity = createDefaultConfig();
        }

        return aiEntity;
    }

    /**
     * 获取所有AI配置
     * @return AI配置列表
     */
    public List<AiEntity> getAllAiConfigs() {
        return aiMapper.selectList(null);
    }

    /**
     * 根据ID获取AI配置
     * @param id 配置ID
     * @return AI配置
     */
    public AiEntity getAiConfigById(Long id) {
        return aiMapper.selectById(id);
    }

    /**
     * 保存或更新AI配置
     * @param introduce 技能介绍
     * @param prompt AI提示词
     * @return 保存的配置
     */
    @Transactional
    public AiEntity saveOrUpdateAiConfig(String introduce, String prompt) {
        // 获取现有配置
        LambdaQueryWrapper<AiEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(AiEntity::getId).last("LIMIT 1");
        AiEntity aiEntity = aiMapper.selectOne(queryWrapper);

        if (aiEntity == null) {
            // 创建新配置
            aiEntity = new AiEntity();
            aiEntity.setIntroduce(introduce);
            aiEntity.setPrompt(prompt);
            aiEntity.setCreatedAt(LocalDateTime.now());
            aiEntity.setUpdatedAt(LocalDateTime.now());
            aiMapper.insert(aiEntity);
            log.info("创建新的AI配置，ID: {}", aiEntity.getId());
        } else {
            // 更新现有配置
            aiEntity.setIntroduce(introduce);
            aiEntity.setPrompt(prompt);
            aiEntity.setUpdatedAt(LocalDateTime.now());
            aiMapper.updateById(aiEntity);
            log.info("更新AI配置，ID: {}", aiEntity.getId());
        }

        return aiEntity;
    }

    /**
     * 创建默认配置
     * @return 默认配置
     */
    private AiEntity createDefaultConfig() {
        AiEntity aiEntity = new AiEntity();
        aiEntity.setIntroduce("请在此填写您的技能介绍");
        aiEntity.setPrompt("请在此填写AI提示词模板");
        aiEntity.setCreatedAt(LocalDateTime.now());
        aiEntity.setUpdatedAt(LocalDateTime.now());
        aiMapper.insert(aiEntity);
        log.info("创建默认AI配置，ID: {}", aiEntity.getId());
        return aiEntity;
    }

    /**
     * 删除AI配置
     * @param id 配置ID
     * @return 是否删除成功
     */
    @Transactional
    public boolean deleteAiConfig(Long id) {
        int result = aiMapper.deleteById(id);
        if (result > 0) {
            log.info("删除AI配置成功，ID: {}", id);
            return true;
        }
        return false;
    }
}
