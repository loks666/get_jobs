package com.getjobs.application.controller;

import com.getjobs.application.entity.BossConfigEntity;
import com.getjobs.application.entity.BossOptionEntity;
import com.getjobs.application.service.BossDataService;
import com.getjobs.application.entity.BlacklistEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/boss/config")
public class BossConfigController {

    private final BossDataService bossDataService;

    public BossConfigController(BossDataService bossDataService) {
        this.bossDataService = bossDataService;
    }

    /**
     * 获取所有Boss配置信息（包括所有选项和黑名单）
     */
    @GetMapping
    public Map<String, Object> getAllBossConfig() {
        Map<String, Object> result = new HashMap<>();

        // 获取配置
        BossConfigEntity config = bossDataService.getFirstConfig();
        if (config == null) {
            config = new BossConfigEntity();
        }

        // 获取所有选项并按类型分组
        Map<String, List<BossOptionEntity>> options = new HashMap<>();
        options.put("city", bossDataService.getOptionsByType("city"));
        options.put("industry", bossDataService.getOptionsByType("industry"));
        options.put("experience", bossDataService.getOptionsByType("experience"));
        options.put("jobType", bossDataService.getOptionsByType("jobType"));
        options.put("salary", bossDataService.getOptionsByType("salary"));
        options.put("degree", bossDataService.getOptionsByType("degree"));
        options.put("scale", bossDataService.getOptionsByType("scale"));
        options.put("stage", bossDataService.getOptionsByType("stage"));

        // 获取黑名单列表
        List<BlacklistEntity> blacklist = bossDataService.getAllBlacklist();

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
            return bossDataService.saveConfig(config);
        } else {
            // 更新现有配置
            return bossDataService.updateConfig(config);
        }
    }

    /**
     * 获取指定类型的选项列表
     */
    @GetMapping("/options/{type}")
    public List<BossOptionEntity> getOptionsByType(@PathVariable String type) {
        return bossDataService.getOptionsByType(type);
    }

    /**
     * 获取黑名单列表
     */
    @GetMapping("/blacklist")
    public List<BlacklistEntity> getBlacklist() {
        return bossDataService.getAllBlacklist();
    }

    /**
     * 添加黑名单
     */
    @PostMapping("/blacklist")
    public BlacklistEntity addBlacklist(@RequestBody BlacklistEntity blacklist) {
        // 添加黑名单，将keyword映射到value
        String value = blacklist.getValue() != null ? blacklist.getValue() : "";
        String type = blacklist.getType() != null ? blacklist.getType() : "boss";

        boolean success = bossDataService.addBlacklist(type, value);
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
        // 直接通过ID查找并删除
        List<BlacklistEntity> allBlacklist = bossDataService.getAllBlacklist();
        BlacklistEntity entity = allBlacklist.stream()
                .filter(e -> e.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (entity != null) {
            return bossDataService.removeBlacklist(entity.getType(), entity.getValue());
        }
        return false;
    }
}
