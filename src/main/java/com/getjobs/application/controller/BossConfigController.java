package com.getjobs.application.controller;

import com.getjobs.application.entity.BossConfigEntity;
import com.getjobs.application.entity.BossOptionEntity;
import com.getjobs.application.service.BossConfigService;
import com.getjobs.application.service.BossOptionService;
import com.getjobs.application.service.BlacklistService;
import com.getjobs.application.entity.BlacklistEntity;
import com.getjobs.application.mapper.BlacklistMapper;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/boss/config")
public class BossConfigController {

    private final BossConfigService bossConfigService;
    private final BossOptionService bossOptionService;
    private final BlacklistService blacklistService;
    private final BlacklistMapper blacklistMapper;

    public BossConfigController(
            BossConfigService bossConfigService,
            BossOptionService bossOptionService,
            BlacklistService blacklistService,
            BlacklistMapper blacklistMapper) {
        this.bossConfigService = bossConfigService;
        this.bossOptionService = bossOptionService;
        this.blacklistService = blacklistService;
        this.blacklistMapper = blacklistMapper;
    }

    /**
     * 获取所有Boss配置信息（包括所有选项和黑名单）
     */
    @GetMapping
    public Map<String, Object> getAllBossConfig() {
        Map<String, Object> result = new HashMap<>();

        // 获取配置
        BossConfigEntity config = bossConfigService.getFirstConfig();
        if (config == null) {
            config = new BossConfigEntity();
        }

        // 获取所有选项并按类型分组
        Map<String, List<BossOptionEntity>> options = new HashMap<>();
        options.put("city", bossOptionService.getOptionsByType("city"));
        options.put("industry", bossOptionService.getOptionsByType("industry"));
        options.put("experience", bossOptionService.getOptionsByType("experience"));
        options.put("jobType", bossOptionService.getOptionsByType("jobType"));
        options.put("salary", bossOptionService.getOptionsByType("salary"));
        options.put("degree", bossOptionService.getOptionsByType("degree"));
        options.put("scale", bossOptionService.getOptionsByType("scale"));
        options.put("stage", bossOptionService.getOptionsByType("stage"));

        // 获取黑名单列表
        List<BlacklistEntity> blacklist = blacklistService.getAllBlacklist();

        result.put("config", config);
        result.put("options", options);
        result.put("blacklist", blacklist);

        return result;
    }

    /**
     * 更新Boss配置
     */
    @PutMapping
    public BossConfigEntity updateConfig(@RequestBody BossConfigEntity config) {
        if (config.getId() == null) {
            // 如果没有ID，创建新配置
            return bossConfigService.saveConfig(config);
        } else {
            // 更新现有配置
            return bossConfigService.updateConfig(config);
        }
    }

    /**
     * 获取指定类型的选项列表
     */
    @GetMapping("/options/{type}")
    public List<BossOptionEntity> getOptionsByType(@PathVariable String type) {
        return bossOptionService.getOptionsByType(type);
    }

    /**
     * 获取黑名单列表
     */
    @GetMapping("/blacklist")
    public List<BlacklistEntity> getBlacklist() {
        return blacklistService.getAllBlacklist();
    }

    /**
     * 添加黑名单
     */
    @PostMapping("/blacklist")
    public BlacklistEntity addBlacklist(@RequestBody BlacklistEntity blacklist) {
        // 添加黑名单，将keyword映射到value
        String value = blacklist.getValue() != null ? blacklist.getValue() : "";
        String type = blacklist.getType() != null ? blacklist.getType() : "boss";

        boolean success = blacklistService.addBlacklist(type, value);
        if (success) {
            // 添加成功，返回新创建的实体
            blacklist.setId(null); // 重新从数据库获取
            return blacklist;
        } else {
            // 已存在或添加失败
            return blacklist;
        }
    }

    /**
     * 删除黑名单
     */
    @DeleteMapping("/blacklist/{id}")
    public boolean deleteBlacklist(@PathVariable Long id) {
        BlacklistEntity entity = blacklistMapper.selectById(id);
        if (entity != null) {
            return blacklistService.removeBlacklist(entity.getType(), entity.getValue());
        }
        return false;
    }
}
