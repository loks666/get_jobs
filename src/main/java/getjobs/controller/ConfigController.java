package getjobs.controller;

import getjobs.common.dto.ConfigDTO;
import getjobs.common.enums.RecruitmentPlatformEnum;
import getjobs.repository.entity.ConfigEntity;
import getjobs.service.ConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @PostMapping("/boss")
    public ResponseEntity<Map<String, Object>> saveBossConfig(@RequestBody ConfigDTO dto) {
        ConfigEntity entity = toEntity(dto);
        // 自动绑定平台类型为boss
        entity.setPlatformType(RecruitmentPlatformEnum.BOSS_ZHIPIN.getPlatformCode());
        entity = configService.save(entity);
        ConfigDTO.reload();

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("id", entity.getId());
        resp.put("platformType", entity.getPlatformType());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/boss")
    public ResponseEntity<ConfigEntity> loadBossConfig() {
        ConfigEntity entity = configService.load();
        return ResponseEntity.ok(entity);
    }

    @PostMapping("/job51")
    public ResponseEntity<Map<String, Object>> save51JobConfig(@RequestBody ConfigDTO dto) {
        ConfigEntity entity = toEntity(dto);
        // 自动绑定平台类型为51job
        entity.setPlatformType(RecruitmentPlatformEnum.JOB_51.getPlatformCode());
        entity = configService.save(entity);
        ConfigDTO.reload();

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("id", entity.getId());
        resp.put("platformType", entity.getPlatformType());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/job51")
    public ResponseEntity<ConfigEntity> load51JobConfig() {
        ConfigEntity entity = configService.load();
        return ResponseEntity.ok(entity);
    }

    private ConfigEntity toEntity(ConfigDTO dto) {
        ConfigEntity e = new ConfigEntity();
        e.setSayHi(dto.getSayHi());
        e.setKeywords(split(dto.getKeywords()));
        e.setCityCode(split(dto.getCityCode()));
        e.setIndustry(split(dto.getIndustry()));
        e.setExperience(wrap(dto.getExperience()));
        e.setJobType(dto.getJobType());
        e.setSalary(dto.getSalary());
        e.setExpectedPosition(dto.getExpectedPosition());
        e.setDegree(wrap(dto.getDegree()));
        e.setScale(wrap(dto.getScale()));
        e.setStage(wrap(dto.getStage()));
        e.setEnableAIJobMatchDetection(Boolean.TRUE.equals(dto.getEnableAIJobMatchDetection()));
        e.setEnableAIGreeting(Boolean.TRUE.equals(dto.getEnableAIGreeting()));
        e.setFilterDeadHR(Boolean.TRUE.equals(dto.getFilterDeadHR()));
        e.setSendImgResume(Boolean.TRUE.equals(dto.getSendImgResume()));
        e.setResumeImagePath(dto.getResumeImagePath());
        e.setResumeContent(dto.getResumeContent());
        if (dto.getMinSalary() != null && dto.getMaxSalary() != null && dto.getMinSalary() > 0
                && dto.getMaxSalary() >= dto.getMinSalary()) {
            e.setExpectedSalary(Arrays.asList(dto.getMinSalary(), dto.getMaxSalary()));
        }
        e.setWaitTime(dto.getWaitTime());
        e.setKeyFilter(Boolean.TRUE.equals(dto.getKeyFilter()));
        e.setRecommendJobs(Boolean.TRUE.equals(dto.getRecommendJobs()));
        e.setCheckStateOwned(Boolean.TRUE.equals(dto.getCheckStateOwned()));
        e.setCustomCityCode(dto.getCustomCityCode());
        e.setDeadStatus(dto.getDeadStatus());
        return e;
    }

    private java.util.List<String> split(String csv) {
        if (csv == null || csv.trim().isEmpty())
            return java.util.Collections.emptyList();
        String[] arr = csv.split(",");
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String s : arr) {
            String t = s.trim();
            if (!t.isEmpty())
                list.add(t);
        }
        return list;
    }

    private java.util.List<String> wrap(String v) {
        if (v == null || v.trim().isEmpty())
            return java.util.Collections.emptyList();
        return java.util.Collections.singletonList(v.trim());
    }

}
