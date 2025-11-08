package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.getjobs.application.entity.BlacklistEntity;
import com.getjobs.application.entity.BossConfigEntity;
import com.getjobs.application.entity.BossIndustryEntity;
import com.getjobs.application.entity.BossOptionEntity;
import com.getjobs.application.mapper.BlacklistMapper;
import com.getjobs.application.mapper.BossJobDataMapper;
import com.getjobs.application.entity.BossJobDataEntity;
import com.getjobs.application.mapper.BossConfigMapper;
import com.getjobs.application.mapper.BossIndustryMapper;
import com.getjobs.application.mapper.BossOptionMapper;
import com.getjobs.worker.boss.BossConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Boss数据服务
 * 统一管理所有Boss相关的数据访问和配置加载
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BossDataService {

    private final BossOptionMapper bossOptionMapper;
    private final BossIndustryMapper bossIndustryMapper;
    private final BossConfigMapper bossConfigMapper;
    private final BlacklistMapper blacklistMapper;
    private final BossJobDataMapper bossJobDataMapper;
    private final javax.sql.DataSource dataSource;

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

    // ==================== boss_data（岗位数据）相关方法 ====================

    /**
     * 确保 boss_data 表的列顺序以 encrypt_id、encrypt_user_id 开头。
     * 若不满足，则进行一次在线迁移：创建新表、复制数据、替换旧表。
     */
    public void ensureBossDataColumnOrder() {
        java.sql.Connection conn = null;
        try {
            conn = dataSource.getConnection();
            try (java.sql.Statement stmt = conn.createStatement()) {
                java.util.List<String> cols = new java.util.ArrayList<>();
                try (java.sql.ResultSet rs = stmt.executeQuery("PRAGMA table_info('boss_data')")) {
                    while (rs.next()) {
                        cols.add(rs.getString("name"));
                    }
                }
                if (cols.isEmpty()) return; // 表不存在或无列
                boolean needMigrate = true;
                if (cols.size() >= 3) {
                    String c0 = cols.get(0) == null ? "" : cols.get(0).toLowerCase();
                    String c1 = cols.get(1) == null ? "" : cols.get(1).toLowerCase();
                    String c2 = cols.get(2) == null ? "" : cols.get(2).toLowerCase();
                    // 允许第一列是 id 或 encrypt_id，但要求前两列满足 encrypt_id、encrypt_user_id 顺序
                    if ("id".equals(c0) && "encrypt_id".equals(c1) && "encrypt_user_id".equals(c2)) {
                        needMigrate = false;
                    } else if ("encrypt_id".equals(c0) && "encrypt_user_id".equals(c1)) {
                        needMigrate = false;
                    }
                }
                if (!needMigrate) return;

                stmt.execute("BEGIN TRANSACTION");
                // 新表：将 encrypt_id、encrypt_user_id 移到最前（紧随 id）
                String createSql = "CREATE TABLE boss_data_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "encrypt_id TEXT, " +
                        "encrypt_user_id TEXT, " +
                        "company_name TEXT, " +
                        "job_name TEXT, " +
                        "salary TEXT, " +
                        "location TEXT, " +
                        "experience TEXT, " +
                        "degree TEXT, " +
                        "hr_name TEXT, " +
                        "hr_position TEXT, " +
                        "hr_active_status TEXT, " +
                        "delivery_status TEXT, " +
                        "job_description TEXT, " +
                        "job_url TEXT, " +
                        "recruitment_status TEXT, " +
                        "company_address TEXT, " +
                        "industry TEXT, " +
                        "introduce TEXT, " +
                        "financing_stage TEXT, " +
                        "company_scale TEXT, " +
                        "created_at TEXT, " +
                        "updated_at TEXT" +
                        ")";
                stmt.execute(createSql);

                String copySql = "INSERT INTO boss_data_new (" +
                        "id, encrypt_id, encrypt_user_id, company_name, job_name, salary, location, experience, degree, " +
                        "hr_name, hr_position, hr_active_status, delivery_status, job_description, job_url, recruitment_status, " +
                        "company_address, industry, introduce, financing_stage, company_scale, created_at, updated_at" +
                        ") SELECT " +
                        "id, encrypt_id, encrypt_user_id, company_name, job_name, salary, location, experience, degree, " +
                        "hr_name, hr_position, hr_active_status, delivery_status, job_description, job_url, recruitment_status, " +
                        "company_address, industry, introduce, financing_stage, company_scale, created_at, updated_at " +
                        "FROM boss_data";
                stmt.execute(copySql);

                stmt.execute("DROP TABLE boss_data");
                stmt.execute("ALTER TABLE boss_data_new RENAME TO boss_data");
                stmt.execute("COMMIT");
                log.info("已调整 boss_data 表列顺序：将 encrypt_id、encrypt_user_id 前置");
            }
        } catch (Exception e) {
            log.warn("调整 boss_data 列顺序失败：{}", e.getMessage());
            try { if (conn != null) conn.createStatement().execute("ROLLBACK"); } catch (Exception ignore) {}
        } finally {
            try { if (conn != null) conn.close(); } catch (Exception ignore) {}
        }
    }

    /**
     * 判断岗位是否已存在（相同 encrypt_id AND encrypt_user_id）
     */
    public boolean existsBossJob(String encryptId, String encryptUserId) {
        if (encryptId == null || encryptUserId == null) return false;
        QueryWrapper<BossJobDataEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("encrypt_id", encryptId)
                .eq("encrypt_user_id", encryptUserId)
                .last("LIMIT 1");
        Long count = bossJobDataMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    /**
     * 仅根据 encrypt_id 判断是否存在（当 encrypt_user_id 缺失时的降级策略）
     */
    public boolean existsBossJobByEncryptId(String encryptId) {
        if (encryptId == null) return false;
        QueryWrapper<BossJobDataEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("encrypt_id", encryptId)
                .last("LIMIT 1");
        Long count = bossJobDataMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    /**
     * 插入新岗位数据（默认 delivery_status 为 未投递，或外部传入值）
     */
    public void insertBossJob(BossJobDataEntity entity) {
        if (entity == null) return;
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        bossJobDataMapper.insert(entity);
    }

    /**
     * 更新投递状态（WHERE encrypt_id = ? AND encrypt_user_id = ?）
     */
    public void updateDeliveryStatus(String encryptId, String encryptUserId, String status) {
        if (encryptId == null || status == null) return;
        BossJobDataEntity update = new BossJobDataEntity();
        update.setDeliveryStatus(status);
        update.setUpdatedAt(LocalDateTime.now());
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<BossJobDataEntity> uw =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        uw.eq("encrypt_id", encryptId);
        if (encryptUserId != null) {
            uw.eq("encrypt_user_id", encryptUserId);
        }
        bossJobDataMapper.update(update, uw);
    }

    // ==================== 投递分析（Dashboard）相关方法 ====================

    /**
     * 薪资解析结果
     */
    public static class SalaryInfo {
        public Integer minK;      // 最小K（单位：K/月）
        public Integer maxK;      // 最大K（单位：K/月）
        public Integer months;    // 月数（默认12）
        public Double medianK;    // 中位数K
        public Long annualTotal;  // 年包（单位：元）
    }

    /**
     * 解析薪资字符串，支持示例：
     *  - 20-40K
     *  - 35-65K·16薪
     *  - 30K·15薪
     *  - 面议（返回null）
     */
    public static SalaryInfo parseSalary(String salary) {
        if (salary == null) return null;
        String s = salary.trim();
        if (s.isEmpty()) return null;
        if (s.contains("面议")) return null;
        s = s.replace(" ", "");

        Integer months = 12;
        // 提取 months=xx（如 ·16薪）
        Matcher mMonths = Pattern.compile("[·\\.\\-]?([0-9]+)薪").matcher(s);
        if (mMonths.find()) {
            try { months = Integer.parseInt(mMonths.group(1)); } catch (Exception ignore) {}
            // 去掉薪资后缀以便解析区间
            s = s.substring(0, mMonths.start());
        }

        // 提取区间或单值（K/k）
        Integer minK = null, maxK = null;
        Matcher mRange = Pattern.compile("^(\\d+)-(\\d+)[Kk]$").matcher(s);
        Matcher mSingle = Pattern.compile("^(\\d+)[Kk]$").matcher(s);
        if (mRange.find()) {
            minK = Integer.parseInt(mRange.group(1));
            maxK = Integer.parseInt(mRange.group(2));
        } else if (mSingle.find()) {
            minK = Integer.parseInt(mSingle.group(1));
            maxK = minK;
        } else {
            // 尝试更宽松解析：去掉非数字和K以外字符
            String cleaned = s.replaceAll("[^0-9Kk\\-]", "");
            mRange = Pattern.compile("^(\\d+)-(\\d+)[Kk]$").matcher(cleaned);
            mSingle = Pattern.compile("^(\\d+)[Kk]$").matcher(cleaned);
            if (mRange.find()) {
                minK = Integer.parseInt(mRange.group(1));
                maxK = Integer.parseInt(mRange.group(2));
            } else if (mSingle.find()) {
                minK = Integer.parseInt(mSingle.group(1));
                maxK = minK;
            }
        }

        if (minK == null || maxK == null) return null;

        SalaryInfo info = new SalaryInfo();
        info.minK = minK;
        info.maxK = maxK;
        info.months = months != null ? months : 12;
        info.medianK = (minK + maxK) / 2.0;
        info.annualTotal = Math.round(info.medianK * 1000 * info.months);
        return info;
    }

    /** KPI 指标 */
    public static class Kpi {
        public long total;
        public long delivered;
        public long pending;
        public long filtered;
        public long failed;
        public Double avgMonthlyK; // 平均中位数K
    }

    /** 通用 name-value 项 */
    public static class NameValue {
        public String name;
        public long value;
        public NameValue() {}
        public NameValue(String name, long value) { this.name = name; this.value = value; }
    }

    /** 薪资桶 */
    public static class BucketValue {
        public String bucket;
        public long value;
        public BucketValue() {}
        public BucketValue(String bucket, long value) { this.bucket = bucket; this.value = value; }
    }

    /** 图表集合 */
    public static class Charts {
        public List<NameValue> byStatus;
        public List<NameValue> byCity;
        public List<NameValue> byIndustry;
        public List<NameValue> byCompany;
        public List<NameValue> byExperience;
        public List<NameValue> byDegree;
        public List<BucketValue> salaryBuckets;
        public List<NameValue> dailyTrend; // date 作为 name
        public List<NameValue> hrActivity;
    }

    /** 统计响应 */
    public static class StatsResponse {
        public Kpi kpi;
        public Charts charts;
    }

    /** 列表分页响应 */
    public static class PagedResult {
        public List<BossJobDataEntity> items;
        public long total;
        public int page;
        public int size;
    }

    /**
     * 获取投递分析统计与图表数据
     */
    public StatsResponse getBossStats() {
        StatsResponse resp = new StatsResponse();
        resp.kpi = new Kpi();
        Charts charts = new Charts();
        charts.byStatus = new ArrayList<>();
        charts.byCity = new ArrayList<>();
        charts.byIndustry = new ArrayList<>();
        charts.byCompany = new ArrayList<>();
        charts.byExperience = new ArrayList<>();
        charts.byDegree = new ArrayList<>();
        charts.salaryBuckets = new ArrayList<>();
        charts.dailyTrend = new ArrayList<>();
        charts.hrActivity = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            // KPI 基本计数
            resp.kpi.total = scalarCount(conn, "SELECT COUNT(*) FROM boss_data");
            resp.kpi.delivered = scalarCount(conn, "SELECT COUNT(*) FROM boss_data WHERE delivery_status='已投递'");
            resp.kpi.pending = scalarCount(conn, "SELECT COUNT(*) FROM boss_data WHERE delivery_status='未投递'");
            resp.kpi.filtered = scalarCount(conn, "SELECT COUNT(*) FROM boss_data WHERE delivery_status='已过滤'");
            resp.kpi.failed = scalarCount(conn, "SELECT COUNT(*) FROM boss_data WHERE delivery_status='投递失败'");

            // 平均中位数K（忽略面议）
            double sumMedian = 0.0; long countMedian = 0;
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT salary FROM boss_data WHERE salary IS NOT NULL")) {
                while (rs.next()) {
                    String s = rs.getString(1);
                    SalaryInfo info = parseSalary(s);
                    if (info != null && info.medianK != null) {
                        sumMedian += info.medianK;
                        countMedian++;
                    }
                }
            }
            resp.kpi.avgMonthlyK = countMedian > 0 ? Math.round((sumMedian / countMedian) * 100.0) / 100.0 : null;

            // byStatus
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT delivery_status, COUNT(*) AS cnt FROM boss_data GROUP BY delivery_status")) {
                while (rs.next()) charts.byStatus.add(new NameValue(nullSafe(rs.getString(1)), rs.getLong(2)));
            }

            // byCity TOP10（保留）
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT location, COUNT(*) AS cnt FROM boss_data GROUP BY location ORDER BY cnt DESC LIMIT 10")) {
                while (rs.next()) charts.byCity.add(new NameValue(nullSafe(rs.getString(1)), rs.getLong(2)));
            }

            // byIndustry TOP10（新增）
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT industry, COUNT(*) AS cnt FROM boss_data GROUP BY industry ORDER BY cnt DESC LIMIT 10")) {
                while (rs.next()) charts.byIndustry.add(new NameValue(nullSafe(rs.getString(1)), rs.getLong(2)));
            }

            // byCompany TOP10
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT company_name, COUNT(*) AS cnt FROM boss_data GROUP BY company_name ORDER BY cnt DESC LIMIT 10")) {
                while (rs.next()) charts.byCompany.add(new NameValue(nullSafe(rs.getString(1)), rs.getLong(2)));
            }

            // byExperience
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT experience, COUNT(*) AS cnt FROM boss_data GROUP BY experience")) {
                while (rs.next()) charts.byExperience.add(new NameValue(nullSafe(rs.getString(1)), rs.getLong(2)));
            }

            // byDegree
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT degree, COUNT(*) AS cnt FROM boss_data GROUP BY degree")) {
                while (rs.next()) charts.byDegree.add(new NameValue(nullSafe(rs.getString(1)), rs.getLong(2)));
            }

            // dailyTrend（按日期聚合）
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT substr(created_at,1,10) AS d, COUNT(*) AS cnt FROM boss_data GROUP BY d ORDER BY d")) {
                while (rs.next()) charts.dailyTrend.add(new NameValue(nullSafe(rs.getString(1)), rs.getLong(2)));
            }

            // hrActivity（仅统计活跃状态非空的 hr_name 计数）
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT hr_name, COUNT(*) AS cnt FROM boss_data WHERE hr_active_status IS NOT NULL AND TRIM(hr_active_status) <> '' GROUP BY hr_name")) {
                while (rs.next()) charts.hrActivity.add(new NameValue(nullSafe(rs.getString(1)), rs.getLong(2)));
            }

            // salaryBuckets（基于中位数K按档统计，动态上限）
            long b0_10=0,b10_15=0,b15_20=0,b20_top=0,b_ge_top=0;
            double maxMedian = 0.0;
            List<Double> medians = new ArrayList<>();
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT salary FROM boss_data WHERE salary IS NOT NULL")) {
                while (rs.next()) {
                    SalaryInfo info = parseSalary(rs.getString(1));
                    if (info == null || info.medianK == null) continue;
                    double m = info.medianK;
                    medians.add(m);
                    if (m > maxMedian) maxMedian = m;
                }
            }
            int topEdge = (int) Math.ceil(maxMedian / 5.0) * 5; // 向上取整到5的倍数
            if (topEdge <= 20) topEdge = 25; // 避免区间过窄
            for (double m : medians) {
                if (m < 10) b0_10++;
                else if (m < 15) b10_15++;
                else if (m < 20) b15_20++;
                else if (m < topEdge) b20_top++;
                else b_ge_top++;
            }
            charts.salaryBuckets.add(new BucketValue("0-10K", b0_10));
            charts.salaryBuckets.add(new BucketValue("10-15K", b10_15));
            charts.salaryBuckets.add(new BucketValue("15-20K", b15_20));
            charts.salaryBuckets.add(new BucketValue("20-" + topEdge + "K", b20_top));
            charts.salaryBuckets.add(new BucketValue(">=" + topEdge + "K", b_ge_top));

            resp.charts = charts;
            return resp;
        } catch (Exception e) {
            log.error("获取Boss统计失败: {}", e.getMessage(), e);
            // 失败时返回空集合，避免前端崩溃
            resp.charts = charts;
            return resp;
        }
    }

    /**
     * 获取投递分析统计与图表数据（按筛选条件）
     */
    public StatsResponse getBossStats(
            List<String> statuses,
            String location,
            String experience,
            String degree,
            Double minK,
            Double maxK,
            String keyword,
            boolean filterHeadhunter
    ) {
        StatsResponse resp = new StatsResponse();
        resp.kpi = new Kpi();
        Charts charts = new Charts();
        charts.byStatus = new ArrayList<>();
        charts.byCity = new ArrayList<>();
        charts.byIndustry = new ArrayList<>();
        charts.byCompany = new ArrayList<>();
        charts.byExperience = new ArrayList<>();
        charts.byDegree = new ArrayList<>();
        charts.salaryBuckets = new ArrayList<>();
        charts.dailyTrend = new ArrayList<>();
        charts.hrActivity = new ArrayList<>();

        try {
            // 构造与列表相同的筛选条件
            QueryWrapper<BossJobDataEntity> wrapper = new QueryWrapper<>();
            if (statuses != null && !statuses.isEmpty()) {
                wrapper.in("delivery_status", statuses);
            }
            if (StringUtils.isNotBlank(location)) wrapper.eq("location", location);
            if (StringUtils.isNotBlank(experience)) wrapper.eq("experience", experience);
            if (StringUtils.isNotBlank(degree)) wrapper.eq("degree", degree);

            if (StringUtils.isNotBlank(keyword)) {
                wrapper.and(w -> w.like("company_name", keyword)
                        .or().like("job_name", keyword)
                        .or().like("hr_name", keyword));
            }
            if (filterHeadhunter) {
                wrapper.and(w -> w.isNull("hr_position").or().notLike("hr_position", "猎头"));
            }
            wrapper.orderByDesc("created_at");

            List<BossJobDataEntity> all = bossJobDataMapper.selectList(wrapper);

            // 内存进行薪资区间过滤（按中位数K）
            List<BossJobDataEntity> filtered = new ArrayList<>();
            double sumMedian = 0.0; long countMedian = 0;
            for (BossJobDataEntity e : all) {
                SalaryInfo info = parseSalary(e.getSalary());
                boolean passSalary;
                if (minK == null && maxK == null) {
                    passSalary = true;
                } else {
                    if (info == null || info.medianK == null) passSalary = false; // 面议或不可解析
                    else {
                        boolean ok = true;
                        if (minK != null) ok = ok && (info.medianK >= minK);
                        if (maxK != null) ok = ok && (info.medianK <= maxK);
                        passSalary = ok;
                    }
                }
                if (passSalary) {
                    filtered.add(e);
                    if (info != null && info.medianK != null) { sumMedian += info.medianK; countMedian++; }
                }
            }

            // KPI
            resp.kpi.total = filtered.size();
            resp.kpi.delivered = filtered.stream().filter(e -> "已投递".equals(e.getDeliveryStatus())).count();
            resp.kpi.pending = filtered.stream().filter(e -> "未投递".equals(e.getDeliveryStatus())).count();
            resp.kpi.filtered = filtered.stream().filter(e -> "已过滤".equals(e.getDeliveryStatus())).count();
            resp.kpi.failed = filtered.stream().filter(e -> "投递失败".equals(e.getDeliveryStatus())).count();
            resp.kpi.avgMonthlyK = countMedian > 0 ? Math.round((sumMedian / countMedian) * 100.0) / 100.0 : null;

            // Charts - 分组聚合（在内存中统计）
            Map<String, Long> byStatus = filtered.stream()
                    .collect(Collectors.groupingBy(e -> nullSafe(e.getDeliveryStatus()), Collectors.counting()));
            byStatus.forEach((k, v) -> charts.byStatus.add(new NameValue(k, v)));

            Map<String, Long> byCity = filtered.stream()
                    .collect(Collectors.groupingBy(e -> nullSafe(e.getLocation()), Collectors.counting()));
            byCity.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(10)
                    .forEach(en -> charts.byCity.add(new NameValue(en.getKey(), en.getValue())));

            // byIndustry TOP10
            Map<String, Long> byIndustry = filtered.stream()
                    .collect(Collectors.groupingBy(e -> nullSafe(e.getIndustry()), Collectors.counting()));
            byIndustry.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(10)
                    .forEach(en -> charts.byIndustry.add(new NameValue(en.getKey(), en.getValue())));

            Map<String, Long> byCompany = filtered.stream()
                    .collect(Collectors.groupingBy(e -> nullSafe(e.getCompanyName()), Collectors.counting()));
            byCompany.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(10)
                    .forEach(en -> charts.byCompany.add(new NameValue(en.getKey(), en.getValue())));

            Map<String, Long> byExp = filtered.stream()
                    .collect(Collectors.groupingBy(e -> nullSafe(e.getExperience()), Collectors.counting()));
            byExp.forEach((k, v) -> charts.byExperience.add(new NameValue(k, v)));

            Map<String, Long> byDeg = filtered.stream()
                    .collect(Collectors.groupingBy(e -> nullSafe(e.getDegree()), Collectors.counting()));
            byDeg.forEach((k, v) -> charts.byDegree.add(new NameValue(k, v)));

            // dailyTrend（created_at 到天）
            Map<String, Long> byDay = filtered.stream()
                    .collect(Collectors.groupingBy(e -> {
                        LocalDateTime t = e.getCreatedAt();
                        return t == null ? "未知" : String.format("%04d-%02d-%02d", t.getYear(), t.getMonthValue(), t.getDayOfMonth());
                    }, Collectors.counting()));
            byDay.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(en -> charts.dailyTrend.add(new NameValue(en.getKey(), en.getValue())));

            // hrActivity（活跃状态非空的 hr_name 计数）
            Map<String, Long> hrAct = filtered.stream()
                    .filter(e -> e.getHrActiveStatus() != null && !e.getHrActiveStatus().trim().isEmpty())
                    .collect(Collectors.groupingBy(e -> nullSafe(e.getHrName()), Collectors.counting()));
            hrAct.forEach((k, v) -> charts.hrActivity.add(new NameValue(k, v)));

            // salaryBuckets（动态上限）
            long b0_10=0,b10_15=0,b15_20=0,b20_top=0,b_ge_top=0;
            double maxMedian = 0.0;
            List<Double> medians = new ArrayList<>();
            for (BossJobDataEntity e : filtered) {
                SalaryInfo info = parseSalary(e.getSalary());
                if (info == null || info.medianK == null) continue;
                double m = info.medianK;
                medians.add(m);
                if (m > maxMedian) maxMedian = m;
            }
            int topEdge = (int) Math.ceil(maxMedian / 5.0) * 5;
            if (topEdge <= 20) topEdge = 25;
            for (double m : medians) {
                if (m < 10) b0_10++;
                else if (m < 15) b10_15++;
                else if (m < 20) b15_20++;
                else if (m < topEdge) b20_top++;
                else b_ge_top++;
            }
            charts.salaryBuckets.add(new BucketValue("0-10K", b0_10));
            charts.salaryBuckets.add(new BucketValue("10-15K", b10_15));
            charts.salaryBuckets.add(new BucketValue("15-20K", b15_20));
            charts.salaryBuckets.add(new BucketValue("20-" + topEdge + "K", b20_top));
            charts.salaryBuckets.add(new BucketValue(">=" + topEdge + "K", b_ge_top));

            resp.charts = charts;
            return resp;
        } catch (Exception e) {
            log.error("获取Boss筛选统计失败: {}", e.getMessage(), e);
            resp.charts = charts;
            return resp;
        }
    }

    private long scalarCount(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private String nullSafe(String s) { return s == null || s.isEmpty() ? "未知" : s; }

    /**
     * 列表查询（分页 + 筛选 + 关键词 + 薪资区间基于中位数K）
     */
    public PagedResult listBossJobs(
            List<String> statuses,
            String location,
            String experience,
            String degree,
            Double minK,
            Double maxK,
            String keyword,
            int page,
            int size,
            boolean filterHeadhunter
    ) {
        if (page <= 0) page = 1;
        if (size <= 0) size = 20;

        QueryWrapper<BossJobDataEntity> wrapper = new QueryWrapper<>();
        if (statuses != null && !statuses.isEmpty()) {
            wrapper.in("delivery_status", statuses);
        }
        if (StringUtils.isNotBlank(location)) wrapper.eq("location", location);
        if (StringUtils.isNotBlank(experience)) wrapper.eq("experience", experience);
        if (StringUtils.isNotBlank(degree)) wrapper.eq("degree", degree);

        if (StringUtils.isNotBlank(keyword)) {
            wrapper.and(w -> w.like("company_name", keyword)
                    .or().like("job_name", keyword)
                    .or().like("hr_name", keyword));
        }

        // 查询阶段过滤猎头：hr_position 不包含“猎头”或为空
        if (filterHeadhunter) {
            wrapper.and(w -> w.isNull("hr_position").or().notLike("hr_position", "猎头"));
        }

        wrapper.orderByDesc("created_at");

        // 取符合条件的记录（猎头已在查询阶段过滤），随后在内存进行薪资区间过滤与分页
        List<BossJobDataEntity> all = bossJobDataMapper.selectList(wrapper);

        List<BossJobDataEntity> filtered = new ArrayList<>();
        for (BossJobDataEntity e : all) {
            if (minK == null && maxK == null) {
                filtered.add(e);
            } else {
                SalaryInfo info = parseSalary(e.getSalary());
                if (info == null || info.medianK == null) continue; // 面议或不可解析
                boolean ok = true;
                if (minK != null) ok = ok && (info.medianK >= minK);
                if (maxK != null) ok = ok && (info.medianK <= maxK);
                if (ok) filtered.add(e);
            }
        }

        int total = filtered.size();
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(total, from + size);
        List<BossJobDataEntity> pageItems = from >= to ? Collections.emptyList() : filtered.subList(from, to);

        PagedResult result = new PagedResult();
        result.items = pageItems;
        result.total = total;
        result.page = page;
        result.size = size;
        return result;
    }

    /**
     * 刷新数据：执行列顺序检查，并执行 VACUUM 以优化数据库；返回当前总数
     */
    public Map<String, Object> reloadBossData() {
        Map<String, Object> resp = new HashMap<>();
        Connection conn = null;
        try {
            ensureBossDataColumnOrder();
            conn = dataSource.getConnection();
            try (Statement st = conn.createStatement()) {
                try { st.execute("PRAGMA wal_checkpoint(TRUNCATE)"); } catch (Exception ignore) {}
                try { st.execute("VACUUM"); } catch (Exception ignore) {}
            }
            long total = scalarCount(conn, "SELECT COUNT(*) FROM boss_data");
            resp.put("success", true);
            resp.put("message", "刷新完成");
            resp.put("total", total);
        } catch (Exception e) {
            log.warn("刷新boss_data失败: {}", e.getMessage());
            resp.put("success", false);
            resp.put("message", "刷新失败: " + e.getMessage());
        } finally {
            try { if (conn != null) conn.close(); } catch (Exception ignore) {}
        }
        return resp;
    }
}
