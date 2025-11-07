package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.getjobs.application.entity.*;
import com.getjobs.application.mapper.*;
import com.getjobs.worker.boss.BossConfig;
import com.getjobs.worker.utils.JobUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Boss数据服务
 * 统一管理所有Boss相关的数据访问和配置加载
 */
@Service
@Slf4j
public class BossDataService {

    private final BossOptionMapper bossOptionMapper;
    private final BossIndustryMapper bossIndustryMapper;
    private final BossConfigMapper bossConfigMapper;
    private final BlacklistMapper blacklistMapper;

    public BossDataService(
            BossOptionMapper bossOptionMapper,
            BossIndustryMapper bossIndustryMapper,
            BossConfigMapper bossConfigMapper,
            BlacklistMapper blacklistMapper) {
        this.bossOptionMapper = bossOptionMapper;
        this.bossIndustryMapper = bossIndustryMapper;
        this.bossConfigMapper = bossConfigMapper;
        this.blacklistMapper = blacklistMapper;
    }

    // ==================== Option相关方法 ====================

    /**
     * 根据类型获取选项列表
     */
    public List<BossOptionEntity> getOptionsByType(String type) {
        // 确保数据库存在『不限』选项（code=0），并置顶显示
        // city 与 industry 都需要此默认项
        QueryWrapper<BossOptionEntity> checkWrapper = new QueryWrapper<>();
        checkWrapper.eq("type", type);
        checkWrapper.eq("code", com.getjobs.worker.utils.Constant.UNLIMITED_CODE);
        Long count = bossOptionMapper.selectCount(checkWrapper);
        if (count == null || count == 0) {
            BossOptionEntity unlimited = new BossOptionEntity();
            unlimited.setType(type);
            unlimited.setName("不限");
            unlimited.setCode(com.getjobs.worker.utils.Constant.UNLIMITED_CODE);
            // 置顶显示
            unlimited.setSortOrder(0);
            unlimited.setCreatedAt(java.time.LocalDateTime.now());
            unlimited.setUpdatedAt(java.time.LocalDateTime.now());
            bossOptionMapper.insert(unlimited);
        }

        // 排序：city/industry 按 sort_order 优先，其次 id；其他类型维持原有 id 升序
        QueryWrapper<BossOptionEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("type", type);
        if ("city".equals(type) || "industry".equals(type)) {
            // SQLite 下可用：ORDER BY sort_order IS NULL, sort_order ASC, id ASC
            wrapper.last("ORDER BY sort_order IS NULL, sort_order ASC, id ASC");
        } else {
            wrapper.orderByAsc("id");
        }
        return bossOptionMapper.selectList(wrapper);
    }

    /**
     * 获取所有选项
     */
    public List<BossOptionEntity> getAllOptions() {
        return bossOptionMapper.selectList(null);
    }

    /**
     * 根据类型和代码获取选项
     */
    public BossOptionEntity getOptionByTypeAndCode(String type, String code) {
        QueryWrapper<BossOptionEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("type", type);
        wrapper.eq("code", code);
        return bossOptionMapper.selectOne(wrapper);
    }

    /**
     * 根据类型和名称获取代码
     * 如果找不到，返回默认值 "0"
     */
    public String getCodeByTypeAndName(String type, String name) {
        QueryWrapper<BossOptionEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("type", type);
        wrapper.eq("name", name);
        BossOptionEntity entity = bossOptionMapper.selectOne(wrapper);
        return entity != null ? entity.getCode() : "0";
    }

    // ==================== City相关方法 ====================

    /**
     * 根据城市名称获取代码（从 boss_option 表中按 type=city 查询）
     * 如果找不到，返回默认值 "0"
     */
    public String getCityCodeByName(String name) {
        QueryWrapper<BossOptionEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("type", "city");
        wrapper.eq("name", name);
        BossOptionEntity entity = bossOptionMapper.selectOne(wrapper);
        return entity != null ? entity.getCode() : "0";
    }

    // ==================== Industry相关方法 ====================

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

    /**
     * 根据行业名称获取代码
     * 如果找不到，返回默认值 "0"
     */
    public String getIndustryCodeByName(String name) {
        QueryWrapper<BossIndustryEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("name", name);
        BossIndustryEntity entity = bossIndustryMapper.selectOne(wrapper);
        return entity != null ? String.valueOf(entity.getCode()) : "0";
    }

    // ==================== BossConfig相关方法 ====================

    /**
     * 获取所有配置
     */
    public List<BossConfigEntity> getAllConfigs() {
        return bossConfigMapper.selectList(null);
    }

    /**
     * 根据ID获取配置
     */
    public BossConfigEntity getConfigById(Long id) {
        return bossConfigMapper.selectById(id);
    }

    /**
     * 获取第一条配置（通常只有一条）
     */
    public BossConfigEntity getFirstConfig() {
        QueryWrapper<BossConfigEntity> wrapper = new QueryWrapper<>();
        wrapper.last("LIMIT 1");
        return bossConfigMapper.selectOne(wrapper);
    }

    /**
     * 保存配置
     */
    public BossConfigEntity saveConfig(BossConfigEntity config) {
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        bossConfigMapper.insert(config);
        return config;
    }

    /**
     * 更新配置
     */
    public BossConfigEntity updateConfig(BossConfigEntity config) {
        config.setUpdatedAt(LocalDateTime.now());
        bossConfigMapper.updateById(config);
        return config;
    }

    /**
     * 保存或更新（优先更新第一条）配置，支持选择性更新（仅覆盖非空字段）。
     * - 若表中已有记录：合并非空字段并更新该记录
     * - 若表为空：插入新记录
     */
    public BossConfigEntity saveOrUpdateFirstSelective(BossConfigEntity partial) {
        BossConfigEntity existing = getFirstConfig();
        LocalDateTime now = LocalDateTime.now();

        if (existing == null) {
            // 表为空，插入新记录
            partial.setCreatedAt(now);
            partial.setUpdatedAt(now);
            bossConfigMapper.insert(partial);
            return partial;
        }

        // 选择性合并：仅当请求体字段非空时才覆盖
        if (partial.getSayHi() != null) existing.setSayHi(partial.getSayHi());
        if (partial.getDebugger() != null) existing.setDebugger(partial.getDebugger());
        if (partial.getEnableAi() != null) existing.setEnableAi(partial.getEnableAi());
        if (partial.getFilterDeadHr() != null) existing.setFilterDeadHr(partial.getFilterDeadHr());
        if (partial.getSendImgResume() != null) existing.setSendImgResume(partial.getSendImgResume());
        if (partial.getWaitTime() != null) existing.setWaitTime(partial.getWaitTime());

        if (partial.getKeywords() != null) existing.setKeywords(partial.getKeywords());
        if (partial.getCityCode() != null) existing.setCityCode(partial.getCityCode());
        if (partial.getIndustry() != null) existing.setIndustry(partial.getIndustry());
        if (partial.getJobType() != null) existing.setJobType(partial.getJobType());
        if (partial.getExperience() != null) existing.setExperience(partial.getExperience());
        if (partial.getDegree() != null) existing.setDegree(partial.getDegree());
        if (partial.getSalary() != null) existing.setSalary(partial.getSalary());
        if (partial.getScale() != null) existing.setScale(partial.getScale());
        if (partial.getStage() != null) existing.setStage(partial.getStage());

        if (partial.getExpectedSalaryMin() != null) existing.setExpectedSalaryMin(partial.getExpectedSalaryMin());
        if (partial.getExpectedSalaryMax() != null) existing.setExpectedSalaryMax(partial.getExpectedSalaryMax());

        if (partial.getDeadStatus() != null) existing.setDeadStatus(partial.getDeadStatus());

        existing.setUpdatedAt(now);
        bossConfigMapper.updateById(existing);
        return existing;
    }

    /**
     * 删除配置
     */
    public boolean deleteConfig(Long id) {
        return bossConfigMapper.deleteById(id) > 0;
    }

    // ==================== 配置加载方法 ====================

    /**
     * 加载Boss配置
     * 从配置文件和数据库加载完整的Boss配置
     */
    public BossConfig loadBossConfig() {
        // 直接从数据库 boss_config 加载，并将括号列表解析为集合
        BossConfigEntity entity = getFirstConfig();
        BossConfig config = new BossConfig();

        if (entity == null) {
            log.warn("boss_config 表为空，使用默认空配置");
            return config;
        }

        // 文本与布尔/数值
        config.setSayHi(entity.getSayHi());
        config.setDebugger(entity.getDebugger() != null && entity.getDebugger() == 1);
        config.setEnableAI(entity.getEnableAi() != null && entity.getEnableAi() == 1);
        config.setFilterDeadHR(entity.getFilterDeadHr() != null && entity.getFilterDeadHr() == 1);
        config.setSendImgResume(entity.getSendImgResume() != null && entity.getSendImgResume() == 1);
        config.setWaitTime(entity.getWaitTime() != null ? String.valueOf(entity.getWaitTime()) : null);

        // 关键词（允许逗号或括号列表），直接解析为列表
        config.setKeywords(parseListString(entity.getKeywords()));

        // 将中文名转换为代码，供 Worker 使用
        // 城市：单值或列表，统一转换为代码列表
        config.setCityCode(toCodes("city", parseListString(entity.getCityCode())));
        // 行业/经验/学历/规模/阶段：名称或代码 -> 统一为代码列表
        config.setIndustry(toCodes("industry", parseListString(entity.getIndustry())));
        config.setExperience(toCodes("experience", parseListString(entity.getExperience())));
        config.setDegree(toCodes("degree", parseListString(entity.getDegree())));
        config.setScale(toCodes("scale", parseListString(entity.getScale())));
        config.setStage(toCodes("stage", parseListString(entity.getStage())));

        // 职位类型：统一转换为代码，若为列表取第一个；为空时使用不限代码
        List<String> jobTypeCodes = toCodes("jobType", parseListString(entity.getJobType()));
        String jobTypeCode = null;
        if (!jobTypeCodes.isEmpty()) {
            jobTypeCode = jobTypeCodes.get(0);
        } else if (entity.getJobType() != null && !entity.getJobType().trim().isEmpty()) {
            BossOptionEntity byCode = getOptionByTypeAndCode("jobType", entity.getJobType());
            jobTypeCode = byCode != null && byCode.getCode() != null
                    ? byCode.getCode()
                    : getCodeByTypeAndName("jobType", entity.getJobType());
        }
        if (jobTypeCode == null || jobTypeCode.trim().isEmpty()) {
            jobTypeCode = com.getjobs.worker.utils.Constant.UNLIMITED_CODE;
        }
        config.setJobType(jobTypeCode);
        // 薪资：名称或代码 -> 统一为代码列表（用于URL逗号拼接）
        config.setSalary(toCodes("salary", parseListString(entity.getSalary())));

        // 期望薪资（min,max）
        if (entity.getExpectedSalaryMin() != null || entity.getExpectedSalaryMax() != null) {
            config.setExpectedSalary(java.util.Arrays.asList(
                    entity.getExpectedSalaryMin() != null ? entity.getExpectedSalaryMin() : 0,
                    entity.getExpectedSalaryMax() != null ? entity.getExpectedSalaryMax() : 0
            ));
        }

        // HR不在线状态（括号列表字符串）
        config.setDeadStatus(parseListString(entity.getDeadStatus()));

        log.info("已从 boss_config 加载Boss配置，并完成括号列表解析");
        return config;
    }

    /**
     * 解析括号列表或逗号分隔的字符串为列表，例如 "[a,b,c]" 或 "a,b,c"。
     * 空值返回空列表。
     */
    public List<String> parseListString(String raw) {
        if (raw == null || raw.trim().isEmpty()) return java.util.Collections.emptyList();
        String s = raw.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
        }
        if (s.trim().isEmpty()) return java.util.Collections.emptyList();
        return java.util.Arrays.stream(s.split(","))
                .map(String::trim)
                // 去除项内可能存在的双引号，兼容 JSON 数组序列化存储
                .map(str -> str.replaceAll("^\"|\"$", ""))
                .filter(str -> !str.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 将列表转换为括号列表字符串，例如 [a,b,c]
     */
    public String toBracketListString(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return "[" + String.join(",", list) + "]";
    }

    /**
     * 规范化：将传入的代码列表转换为名称列表（若传入已是名称则原样返回）。
     */
    public List<String> toNames(String type, List<String> items) {
        if (items == null || items.isEmpty()) return java.util.Collections.emptyList();
        return items.stream().map(it -> {
            // 若是有效code，直接按code查找并取name
            BossOptionEntity byCode = getOptionByTypeAndCode(type, it);
            if (byCode != null && byCode.getName() != null) {
                return byCode.getName();
            }
            // 否则当作name使用（无需转换）
            return it;
        }).collect(Collectors.toList());
    }

    /**
     * 规范化：将传入的名称列表转换为代码列表（若传入已是代码则原样保留）。
     */
    public List<String> toCodes(String type, List<String> items) {
        if (items == null || items.isEmpty()) return java.util.Collections.emptyList();
        return items.stream().map(it -> {
            // 若是有效code，保留
            BossOptionEntity byCode = getOptionByTypeAndCode(type, it);
            if (byCode != null && byCode.getCode() != null) {
                return byCode.getCode();
            }
            // 否则按name查code
            String code = getCodeByTypeAndName(type, it);
            return code != null ? code : "0";
        }).collect(Collectors.toList());
    }

    /**
     * 统一化城市：将 city 值（可能是 code 或 name 或括号列表）转换为『城市中文名』。
     */
    public String normalizeCityToName(String raw) {
        List<String> list = parseListString(raw);
        String first = list.isEmpty() ? (raw == null ? "" : raw.trim()) : list.get(0);
        if (first.isEmpty()) return "";
        BossOptionEntity byCode = getOptionByTypeAndCode("city", first);
        if (byCode != null && byCode.getName() != null) return byCode.getName();
        // 已是中文名
        return first;
    }

    // ==================== Blacklist相关方法 ====================

    /**
     * 根据类型获取黑名单列表
     *
     * @param type 类型 (company/recruiter/job)
     * @return 黑名单值集合
     */
    public Set<String> getBlacklistByType(String type) {
        LambdaQueryWrapper<BlacklistEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BlacklistEntity::getType, type);
        List<BlacklistEntity> list = blacklistMapper.selectList(wrapper);
        return list.stream()
                .map(BlacklistEntity::getValue)
                .collect(Collectors.toSet());
    }

    /**
     * 获取所有公司黑名单
     */
    public Set<String> getBlackCompanies() {
        return getBlacklistByType("company");
    }

    /**
     * 获取所有招聘者黑名单
     */
    public Set<String> getBlackRecruiters() {
        return getBlacklistByType("recruiter");
    }

    /**
     * 获取所有职位黑名单
     */
    public Set<String> getBlackJobs() {
        return getBlacklistByType("job");
    }

    /**
     * 添加黑名单
     *
     * @param type  类型
     * @param value 值
     * @return 是否成功
     */
    public boolean addBlacklist(String type, String value) {
        // 检查是否已存在
        LambdaQueryWrapper<BlacklistEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BlacklistEntity::getType, type)
                .eq(BlacklistEntity::getValue, value);
        if (blacklistMapper.selectCount(wrapper) > 0) {
            return false; // 已存在
        }

        BlacklistEntity entity = new BlacklistEntity();
        entity.setType(type);
        entity.setValue(value);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return blacklistMapper.insert(entity) > 0;
    }

    /**
     * 批量添加黑名单
     *
     * @param type   类型
     * @param values 值集合
     */
    public void addBlacklistBatch(String type, Set<String> values) {
        values.forEach(value -> addBlacklist(type, value));
    }

    /**
     * 删除黑名单
     *
     * @param type  类型
     * @param value 值
     * @return 是否成功
     */
    public boolean removeBlacklist(String type, String value) {
        LambdaQueryWrapper<BlacklistEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BlacklistEntity::getType, type)
                .eq(BlacklistEntity::getValue, value);
        return blacklistMapper.delete(wrapper) > 0;
    }

    /**
     * 获取所有黑名单
     *
     * @return 所有黑名单实体
     */
    public List<BlacklistEntity> getAllBlacklist() {
        return blacklistMapper.selectList(null);
    }
}
