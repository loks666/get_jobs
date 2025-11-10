package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.getjobs.application.entity.LiepinEntity;
import com.getjobs.application.entity.LiepinConfigEntity;
import com.getjobs.application.entity.LiepinOptionEntity;
import com.getjobs.application.mapper.LiepinConfigMapper;
import com.getjobs.application.mapper.LiepinOptionMapper;
import com.getjobs.application.mapper.LiepinMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.stream.Collectors;

/**
 * 猎聘数据服务
 * 统一管理所有猎聘相关的数据访问和配置加载
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LiepinService {

    private final LiepinConfigMapper liepinConfigMapper;
    private final LiepinOptionMapper liepinOptionMapper;
    // 记录持久化相关依赖（整合自 LiepinRecordService）
    private final LiepinMapper liepinMapper;
    private final DataSource dataSource;

    // ==================== 记录表初始化与快照保存 ====================

    @PostConstruct
    public void ensureTableExists() {
        String createSql = "CREATE TABLE IF NOT EXISTS liepin_data (" +
                " job_id            BIGINT PRIMARY KEY," +
                " job_title         VARCHAR(200)," +
                " job_link          VARCHAR(300)," +
                " job_salary_text   VARCHAR(100)," +
                " job_area          VARCHAR(100)," +
                " job_edu_req       VARCHAR(50)," +
                " job_exp_req       VARCHAR(50)," +
                " job_publish_time  VARCHAR(50)," +
                " comp_id           BIGINT," +
                " comp_name         VARCHAR(200)," +
                " comp_industry     VARCHAR(100)," +
                " comp_scale        VARCHAR(50)," +
                " hr_id             VARCHAR(64)," +
                " hr_name           VARCHAR(50)," +
                " hr_title          VARCHAR(100)," +
                " hr_im_id          VARCHAR(64)," +
                " delivered         INTEGER DEFAULT 0," +
                " create_time       DATETIME," +
                " update_time       DATETIME" +
                ")";
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createSql);
            // 兼容旧库：尝试添加 delivered 列（如已存在则忽略错误）
            try {
                stmt.execute("ALTER TABLE liepin_data ADD COLUMN delivered INTEGER DEFAULT 0");
            } catch (Exception ignored) {}
            // 兼容旧库：尝试移除无数据列（SQLite 3.35+ 支持；不支持则忽略错误）
            try { stmt.execute("ALTER TABLE liepin_data DROP COLUMN job_function"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE liepin_data DROP COLUMN job_city"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE liepin_data DROP COLUMN comp_full_name"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE liepin_data DROP COLUMN comp_kind"); } catch (Exception ignored) {}
            log.info("确保 liepin_data 表已存在");
        } catch (Exception e) {
            log.warn("创建 liepin_data 表失败: {}", e.getMessage());
        }
    }

    /**
     * 保存或更新一条岗位快照（以 job_id 作为主键）
     */
    public void saveOrUpdateSnapshot(LiepinEntity entity) {
        if (entity == null || entity.getJobId() == null) {
            return;
        }
        try {
            LiepinEntity existing = liepinMapper.selectById(entity.getJobId());
            LocalDateTime now = LocalDateTime.now();
            if (existing == null) {
                entity.setCreateTime(now);
                entity.setUpdateTime(now);
                if (entity.getDelivered() == null) entity.setDelivered(0);
                liepinMapper.insert(entity);
            } else {
                // 保留 create_time，更新其他字段与 update_time
                entity.setCreateTime(existing.getCreateTime());
                entity.setUpdateTime(now);
                if (entity.getDelivered() == null) entity.setDelivered(existing.getDelivered());
                liepinMapper.updateById(entity);
            }
        } catch (Exception e) {
            log.warn("保存猎聘岗位快照失败 job_id={}: {}", entity.getJobId(), e.getMessage());
        }
    }

    /**
     * 仅在不存在时插入岗位快照（默认 delivered=0）；存在则跳过
     */
    public void insertSnapshotIfNotExists(LiepinEntity entity) {
        if (entity == null || entity.getJobId() == null) {
            return;
        }
        try {
            LiepinEntity existing = liepinMapper.selectById(entity.getJobId());
            if (existing == null) {
                LocalDateTime now = LocalDateTime.now();
                entity.setCreateTime(now);
                entity.setUpdateTime(now);
                if (entity.getDelivered() == null) entity.setDelivered(0);
                liepinMapper.insert(entity);
            } else {
                // already exists, skip
            }
        } catch (Exception e) {
            log.warn("插入猎聘岗位快照失败 job_id={}: {}", entity.getJobId(), e.getMessage());
        }
    }

    /**
     * 标记岗位为已投递（delivered=1），如存在该记录
     */
    public void markDelivered(Long jobId) {
        if (jobId == null) return;
        try {
            LiepinEntity existing = liepinMapper.selectById(jobId);
            if (existing != null) {
                LiepinEntity update = new LiepinEntity();
                update.setJobId(jobId);
                update.setDelivered(1);
                update.setCreateTime(existing.getCreateTime());
                update.setUpdateTime(LocalDateTime.now());
                liepinMapper.updateById(update);
            }
        } catch (Exception e) {
            log.warn("更新投递状态失败 job_id={}: {}", jobId, e.getMessage());
        }
    }

    /**
     * 批量插入岗位快照（仅不存在时），减少单次网络响应后的数据库操作时间
     */
    public void insertSnapshotsIfNotExistsBatch(java.util.List<LiepinEntity> entities) {
        if (entities == null || entities.isEmpty()) return;

        // 收集待处理的 jobId
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (LiepinEntity e : entities) {
            if (e != null && e.getJobId() != null) ids.add(e.getJobId());
        }
        if (ids.isEmpty()) return;

        // 批量查询已存在的记录
        java.util.List<Long> idList = new java.util.ArrayList<>(ids);
        java.util.List<LiepinEntity> existing = liepinMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<LiepinEntity>()
                        .in("job_id", idList)
        );
        java.util.Set<Long> existingIds = new java.util.HashSet<>();
        if (existing != null) {
            for (LiepinEntity e : existing) {
                if (e != null && e.getJobId() != null) existingIds.add(e.getJobId());
            }
        }

        // 过滤出需要插入的记录
        java.util.List<LiepinEntity> toInsert = new java.util.ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (LiepinEntity e : entities) {
            if (e == null || e.getJobId() == null) continue;
            if (existingIds.contains(e.getJobId())) continue;
            if (e.getCreateTime() == null) e.setCreateTime(now);
            e.setUpdateTime(now);
            if (e.getDelivered() == null) e.setDelivered(0);
            toInsert.add(e);
        }
        if (toInsert.isEmpty()) return;

        // 使用JDBC批量插入以提升性能
        String sql = "INSERT INTO liepin_data (" +
                "job_id, job_title, job_link, job_salary_text, job_area, job_edu_req, job_exp_req, job_publish_time, " +
                "comp_id, comp_name, comp_industry, comp_scale, " +
                "hr_id, hr_name, hr_title, hr_im_id, delivered, create_time, update_time) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (LiepinEntity e : toInsert) {
                // 1 job_id
                if (e.getJobId() == null) ps.setNull(1, Types.BIGINT); else ps.setLong(1, e.getJobId());
                // 2 job_title
                if (e.getJobTitle() == null) ps.setNull(2, Types.VARCHAR); else ps.setString(2, e.getJobTitle());
                // 3 job_link
                if (e.getJobLink() == null) ps.setNull(3, Types.VARCHAR); else ps.setString(3, e.getJobLink());
                // 4 job_salary_text
                if (e.getJobSalaryText() == null) ps.setNull(4, Types.VARCHAR); else ps.setString(4, e.getJobSalaryText());
                // 5 job_area
                if (e.getJobArea() == null) ps.setNull(5, Types.VARCHAR); else ps.setString(5, e.getJobArea());
                // 6 job_edu_req
                if (e.getJobEduReq() == null) ps.setNull(6, Types.VARCHAR); else ps.setString(6, e.getJobEduReq());
                // 7 job_exp_req
                if (e.getJobExpReq() == null) ps.setNull(7, Types.VARCHAR); else ps.setString(7, e.getJobExpReq());
                // 8 job_publish_time
                if (e.getJobPublishTime() == null) ps.setNull(8, Types.VARCHAR); else ps.setString(8, e.getJobPublishTime());
                // 9 comp_id
                if (e.getCompId() == null) ps.setNull(9, Types.BIGINT); else ps.setLong(9, e.getCompId());
                // 10 comp_name
                if (e.getCompName() == null) ps.setNull(10, Types.VARCHAR); else ps.setString(10, e.getCompName());
                // 11 comp_industry
                if (e.getCompIndustry() == null) ps.setNull(11, Types.VARCHAR); else ps.setString(11, e.getCompIndustry());
                // 12 comp_scale
                if (e.getCompScale() == null) ps.setNull(12, Types.VARCHAR); else ps.setString(12, e.getCompScale());
                // 13 hr_id
                if (e.getHrId() == null) ps.setNull(13, Types.VARCHAR); else ps.setString(13, e.getHrId());
                // 14 hr_name
                if (e.getHrName() == null) ps.setNull(14, Types.VARCHAR); else ps.setString(14, e.getHrName());
                // 15 hr_title
                if (e.getHrTitle() == null) ps.setNull(15, Types.VARCHAR); else ps.setString(15, e.getHrTitle());
                // 16 hr_im_id
                if (e.getHrImId() == null) ps.setNull(16, Types.VARCHAR); else ps.setString(16, e.getHrImId());
                // 17 delivered
                if (e.getDelivered() == null) ps.setNull(17, Types.INTEGER); else ps.setInt(17, e.getDelivered());
                // 18 create_time
                if (e.getCreateTime() == null) ps.setNull(18, Types.TIMESTAMP); else ps.setTimestamp(18, Timestamp.valueOf(e.getCreateTime()));
                // 19 update_time
                if (e.getUpdateTime() == null) ps.setNull(19, Types.TIMESTAMP); else ps.setTimestamp(19, Timestamp.valueOf(e.getUpdateTime()));

                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (Exception e) {
            log.warn("批量插入猎聘岗位快照失败: {}", e.getMessage());
        }
    }

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

    // ==================== 分析与列表（参考 BossService） ====================

    public static class SalaryInfo {
        public Integer minK;
        public Integer maxK;
        public Integer months;
        public Double medianK;
        public Long annualTotal;
    }

    /** 解析薪资文本为区间与中位数K（支持“面议”“X-Yk·N薪”等） */
    public static SalaryInfo parseSalary(String salaryText) {
        if (salaryText == null) return null;
        String s = salaryText.trim();
        if (s.isEmpty()) return null;
        if (s.contains("面议")) return null;

        Integer months = null;
        java.util.regex.Matcher mMonths = java.util.regex.Pattern.compile("([0-9]{1,2})[\u00B7\u00D7xX*]?(?:薪|月)").matcher(s);
        if (mMonths.find()) {
            try { months = Integer.parseInt(mMonths.group(1)); } catch (Exception ignored) {}
        }

        String cleaned = s.replaceAll("[·\u00B7\u00D7xX*].*$", "");
        Integer minK = null, maxK = null;
        java.util.regex.Matcher mRange = java.util.regex.Pattern.compile("(\\d+)[Kk].*?(\\d+)[Kk]").matcher(cleaned);
        java.util.regex.Matcher mSingle = java.util.regex.Pattern.compile("^(\\d+)[Kk]$").matcher(cleaned);
        if (mRange.find()) {
            try { minK = Integer.parseInt(mRange.group(1)); maxK = Integer.parseInt(mRange.group(2)); } catch (Exception ignored) {}
        } else if (mSingle.find()) {
            try { minK = Integer.parseInt(mSingle.group(1)); maxK = minK; } catch (Exception ignored) {}
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

    public static class Kpi {
        public long total;
        public long delivered;
        public long pending;
        public long filtered; // 猎聘暂无，置0
        public long failed;   // 猎聘暂无，置0
        public Double avgMonthlyK; // 平均中位数K
    }

    public static class NameValue { public String name; public long value; public NameValue(){} public NameValue(String n,long v){name=n;value=v;} }
    public static class BucketValue { public String bucket; public long value; public BucketValue(){} public BucketValue(String b,long v){bucket=b;value=v;} }

    public static class Charts {
        public List<NameValue> byStatus;
        public List<NameValue> byCity;
        public List<NameValue> byIndustry;
        public List<NameValue> byCompany;
        public List<NameValue> byExperience;
        public List<NameValue> byDegree;
        public List<BucketValue> salaryBuckets;
        public List<NameValue> dailyTrend;
        public List<NameValue> hrActivity;
    }

    public static class StatsResponse {
        public Kpi kpi;
        public Charts charts;
    }

    public static class PagedResult {
        public List<LiepinEntity> items;
        public long total;
        public int page;
        public int size;
    }

    private String nullSafe(String s) { return (s == null || s.trim().isEmpty()) ? "未知" : s.trim(); }

    /**
     * 获取投递分析统计与图表数据（按筛选条件）
     */
    public StatsResponse getLiepinStats(
            List<String> statuses,
            String location,
            String experience,
            String degree,
            Double minK,
            Double maxK,
            String keyword
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
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<LiepinEntity> wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();

            // 状态：已投递/未投递 -> delivered 1/0
            if (statuses != null && !statuses.isEmpty()) {
                Set<Integer> deliveredSet = new HashSet<>();
                for (String s : statuses) {
                    if (s != null) {
                        String t = s.trim();
                        if ("已投递".equals(t)) deliveredSet.add(1);
                        if ("未投递".equals(t)) deliveredSet.add(0);
                    }
                }
                if (!deliveredSet.isEmpty()) {
                    wrapper.in("delivered", deliveredSet);
                }
            }
            if (location != null && !location.trim().isEmpty()) wrapper.eq("job_area", location.trim());
            if (experience != null && !experience.trim().isEmpty()) wrapper.eq("job_exp_req", experience.trim());
            if (degree != null && !degree.trim().isEmpty()) wrapper.eq("job_edu_req", degree.trim());

            if (keyword != null && !keyword.trim().isEmpty()) {
                String kw = keyword.trim();
                wrapper.and(w -> w.like("comp_name", kw)
                        .or().like("job_title", kw)
                        .or().like("hr_name", kw));
            }

            wrapper.orderByDesc("create_time");
            List<LiepinEntity> all = liepinMapper.selectList(wrapper);

            // 薪资区间过滤（按中位数K）
            List<LiepinEntity> filtered = new ArrayList<>();
            double sumMedian = 0.0; long countMedian = 0;
            for (LiepinEntity e : all) {
                SalaryInfo info = parseSalary(e.getJobSalaryText());
                boolean passSalary;
                if (minK == null && maxK == null) passSalary = true;
                else {
                    if (info == null || info.medianK == null) passSalary = false;
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
            resp.kpi.delivered = filtered.stream().filter(e -> Objects.equals(e.getDelivered(), 1)).count();
            resp.kpi.pending = filtered.stream().filter(e -> e.getDelivered() == null || Objects.equals(e.getDelivered(), 0)).count();
            resp.kpi.filtered = 0;
            resp.kpi.failed = 0;
            resp.kpi.avgMonthlyK = countMedian > 0 ? Math.round((sumMedian / countMedian) * 100.0) / 100.0 : null;

            // Charts 聚合
            Map<String, Long> byStatus = filtered.stream()
                    .collect(Collectors.groupingBy(e -> Objects.equals(e.getDelivered(), 1) ? "已投递" : "未投递", Collectors.counting()));
            byStatus.forEach((k, v) -> charts.byStatus.add(new NameValue(k, v)));

            Map<String, Long> byCity = filtered.stream()
                    .collect(Collectors.groupingBy(e -> nullSafe(e.getJobArea()), Collectors.counting()));
            byCity.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(10)
                    .forEach(en -> charts.byCity.add(new NameValue(en.getKey(), en.getValue())));

            Map<String, Long> byIndustry = filtered.stream()
                    .collect(Collectors.groupingBy(e -> nullSafe(e.getCompIndustry()), Collectors.counting()));
            byIndustry.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(10)
                    .forEach(en -> charts.byIndustry.add(new NameValue(en.getKey(), en.getValue())));

            Map<String, Long> byCompany = filtered.stream()
                    .collect(Collectors.groupingBy(e -> nullSafe(e.getCompName()), Collectors.counting()));
            byCompany.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(10)
                    .forEach(en -> charts.byCompany.add(new NameValue(en.getKey(), en.getValue())));

            Map<String, Long> byExp = filtered.stream()
                    .collect(Collectors.groupingBy(e -> nullSafe(e.getJobExpReq()), Collectors.counting()));
            byExp.forEach((k, v) -> charts.byExperience.add(new NameValue(k, v)));

            Map<String, Long> byDeg = filtered.stream()
                    .collect(Collectors.groupingBy(e -> nullSafe(e.getJobEduReq()), Collectors.counting()));
            byDeg.forEach((k, v) -> charts.byDegree.add(new NameValue(k, v)));

            Map<String, Long> byDay = filtered.stream()
                    .collect(Collectors.groupingBy(e -> {
                        LocalDateTime t = e.getCreateTime();
                        return t == null ? "未知" : String.format("%04d-%02d-%02d", t.getYear(), t.getMonthValue(), t.getDayOfMonth());
                    }, Collectors.counting()));
            byDay.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(en -> charts.dailyTrend.add(new NameValue(en.getKey(), en.getValue())));

            Map<String, Long> hrAct = filtered.stream()
                    .filter(e -> e.getHrName() != null && !e.getHrName().trim().isEmpty())
                    .collect(Collectors.groupingBy(e -> nullSafe(e.getHrName()), Collectors.counting()));
            hrAct.forEach((k, v) -> charts.hrActivity.add(new NameValue(k, v)));

            // 薪资桶
            long b0_10=0,b10_15=0,b15_20=0,b20_top=0,b_ge_top=0;
            double maxMedian = 0.0;
            List<Double> medians = new ArrayList<>();
            for (LiepinEntity e : filtered) {
                SalaryInfo info = parseSalary(e.getJobSalaryText());
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
            log.error("获取猎聘统计失败: {}", e.getMessage(), e);
            resp.charts = charts;
            return resp;
        }
    }

    /**
     * 列表查询（分页 + 筛选 + 关键词 + 薪资区间基于中位数K）
     */
    public PagedResult listLiepinJobs(
            List<String> statuses,
            String location,
            String experience,
            String degree,
            Double minK,
            Double maxK,
            String keyword,
            int page,
            int size
    ) {
        if (page <= 0) page = 1;
        if (size <= 0) size = 20;

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<LiepinEntity> wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();

        if (statuses != null && !statuses.isEmpty()) {
            Set<Integer> deliveredSet = new HashSet<>();
            for (String s : statuses) {
                if (s != null) {
                    String t = s.trim();
                    if ("已投递".equals(t)) deliveredSet.add(1);
                    if ("未投递".equals(t)) deliveredSet.add(0);
                }
            }
            if (!deliveredSet.isEmpty()) wrapper.in("delivered", deliveredSet);
        }
        if (location != null && !location.trim().isEmpty()) wrapper.eq("job_area", location.trim());
        if (experience != null && !experience.trim().isEmpty()) wrapper.eq("job_exp_req", experience.trim());
        if (degree != null && !degree.trim().isEmpty()) wrapper.eq("job_edu_req", degree.trim());

        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like("comp_name", kw)
                    .or().like("job_title", kw)
                    .or().like("hr_name", kw));
        }

        wrapper.orderByDesc("create_time");
        List<LiepinEntity> all = liepinMapper.selectList(wrapper);

        List<LiepinEntity> filtered = new ArrayList<>();
        for (LiepinEntity e : all) {
            if (minK == null && maxK == null) {
                filtered.add(e);
            } else {
                SalaryInfo info = parseSalary(e.getJobSalaryText());
                if (info == null || info.medianK == null) continue;
                boolean ok = true;
                if (minK != null) ok = ok && (info.medianK >= minK);
                if (maxK != null) ok = ok && (info.medianK <= maxK);
                if (ok) filtered.add(e);
            }
        }

        int total = filtered.size();
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(total, from + size);

        PagedResult pr = new PagedResult();
        pr.items = filtered.subList(from, to);
        pr.total = total;
        pr.page = page;
        pr.size = size;
        return pr;
    }
}
