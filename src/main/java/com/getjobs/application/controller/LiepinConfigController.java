package com.getjobs.application.controller;

import com.getjobs.application.entity.LiepinConfigEntity;
import com.getjobs.application.entity.LiepinOptionEntity;
import com.getjobs.application.service.LiepinService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/liepin/config")
public class LiepinConfigController {

    private final LiepinService liepinService;

    public LiepinConfigController(LiepinService liepinService) {
        this.liepinService = liepinService;
    }

    /**
     * 获取所有猎聘配置信息（包括所有选项）
     */
    @GetMapping
    public Map<String, Object> getAllLiepinConfig() {
        Map<String, Object> result = new HashMap<>();

        // 获取配置
        LiepinConfigEntity config = liepinService.getFirstConfig();
        if (config == null) {
            config = new LiepinConfigEntity();
        }

        // 获取所有选项并按类型分组
        Map<String, List<LiepinOptionEntity>> options = new HashMap<>();
        options.put("city", liepinService.getOptionsByType("city"));

        result.put("config", config);
        result.put("options", options);

        return result;
    }

    /**
     * 更新猎聘配置
     */
    @PutMapping
    public LiepinConfigEntity updateConfig(@RequestBody LiepinConfigEntity config) {
        // 城市：如果传入的是代码，转换为名称保存；如果是手动输入的名称，直接保存
        if (config.getCity() != null && !config.getCity().isEmpty()) {
            String cityName = liepinService.normalizeCityToName(config.getCity());
            config.setCity(cityName);
        }

        // 为避免每次新增导致错乱：当ID缺失时也执行"选择性更新第一条"策略
        // 若存在ID，按ID更新；否则更新首条记录（若不存在则插入）
        if (config.getId() != null) {
            return liepinService.updateConfig(config);
        }
        return liepinService.saveOrUpdateFirstSelective(config);
    }

    /**
     * 获取指定类型的选项列表
     */
    @GetMapping("/options/{type}")
    public List<LiepinOptionEntity> getOptionsByType(@PathVariable String type) {
        return liepinService.getOptionsByType(type);
    }
}
