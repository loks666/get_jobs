package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.getjobs.application.entity.ZhilianConfigEntity;
import com.getjobs.application.entity.ZhilianOptionEntity;
import com.getjobs.application.entity.ZhilianJobDataEntity;
import com.getjobs.application.mapper.ZhilianConfigMapper;
import com.getjobs.application.mapper.ZhilianOptionMapper;
import com.getjobs.application.mapper.ZhilianJobDataMapper;
import com.getjobs.worker.zhilian.ZhilianConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ZhilianService {
    private final ZhilianConfigMapper zhilianConfigMapper;
    private final ZhilianOptionMapper zhilianOptionMapper;
    private final ZhilianJobDataMapper zhilianJobDataMapper;
    private final DataSource dataSource;

    /** 获取第一条配置（通常只有一条） */
    public ZhilianConfigEntity getFirstConfig() {
        QueryWrapper<ZhilianConfigEntity> wrapper = new QueryWrapper<>();
        wrapper.last("LIMIT 1");
        return zhilianConfigMapper.selectOne(wrapper);
    }

    /** 从专表构建 ZhilianConfig */
    public ZhilianConfig loadZhilianConfig() {
        ZhilianConfigEntity entity = getFirstConfig();
        ZhilianConfig config = new ZhilianConfig();
        if (entity == null) {
            log.warn("zhilian_config 表为空，使用默认空配置");
            config.setKeywords(new ArrayList<>());
            config.setCityCode("0");
            config.setSalary("0");
            return config;
        }

        // 关键词解析：支持逗号或括号列表
        config.setKeywords(parseListString(entity.getKeywords()));

        // 城市：中文名映射到代码；缺省或“不限”映射为 0
        String city = safeTrim(entity.getCityCode());
        if (city == null || city.isEmpty() || "不限".equals(city)) {
            config.setCityCode("0");
        } else if (city.chars().allMatch(Character::isDigit)) {
            config.setCityCode(city);
        } else {
            String code = getCodeByTypeAndName("city", city);
            if (code == null) {
                log.warn("智联配置中的城市 {} 未在 zhilian_option 表中找到，回退为 0", city);
                config.setCityCode("0");
            } else {
                config.setCityCode(code);
            }
        }

        // 薪资：缺省或“不限”映射为 0，其它保持原值
        String salary = safeTrim(entity.getSalary());
        if (salary == null || salary.isEmpty() || "不限".equals(salary)) {
            config.setSalary("0");
        } else {
            config.setSalary(salary);
        }
        return config;
    }

    public List<String> parseListString(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new ArrayList<>();
        String s = raw.trim().replace('，', ',');
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1);
        if (s.trim().isEmpty()) return new ArrayList<>();
        return java.util.Arrays.stream(s.split(","))
                .map(String::trim)
                .map(this::stripWrapperQuotes)
                .filter(str -> !str.isEmpty())
                .collect(Collectors.toList());
    }

    private String safeTrim(String s) { return s == null ? null : s.trim(); }

    private String stripWrapperQuotes(String value) {
        if (value == null || value.length() < 2) return value;
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    /**
     * 选择性更新：若传入 ID 则按 ID 更新；否则更新第一条记录（不存在则插入）
     */
    public ZhilianConfigEntity updateConfig(ZhilianConfigEntity config) {
        if (config == null) return null;
        if (config.getId() != null) {
            zhilianConfigMapper.updateById(config);
            return zhilianConfigMapper.selectById(config.getId());
        }
        return saveOrUpdateFirstSelective(config);
    }

    /**
     * 保存或选择性更新第一条记录（仅覆盖非空字段）
     */
    public ZhilianConfigEntity saveOrUpdateFirstSelective(ZhilianConfigEntity incoming) {
        ZhilianConfigEntity first = getFirstConfig();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (first == null) {
            ZhilianConfigEntity toInsert = new ZhilianConfigEntity();
            toInsert.setKeywords(incoming.getKeywords());
            toInsert.setCityCode(incoming.getCityCode());
            toInsert.setSalary(incoming.getSalary());
            toInsert.setCreatedAt(now);
            toInsert.setUpdatedAt(now);
            zhilianConfigMapper.insert(toInsert);
            return getFirstConfig();
        } else {
            ZhilianConfigEntity toUpdate = new ZhilianConfigEntity();
            toUpdate.setId(first.getId());
            if (incoming.getKeywords() != null) toUpdate.setKeywords(incoming.getKeywords());
            if (incoming.getCityCode() != null) toUpdate.setCityCode(incoming.getCityCode());
            if (incoming.getSalary() != null) toUpdate.setSalary(incoming.getSalary());
            toUpdate.setCreatedAt(first.getCreatedAt());
            toUpdate.setUpdatedAt(now);
            zhilianConfigMapper.updateById(toUpdate);
            return zhilianConfigMapper.selectById(first.getId());
        }
    }

    // ========== Option 辅助 ==========

    public List<ZhilianOptionEntity> getOptionsByType(String type) {
        return zhilianOptionMapper.selectList(
                new QueryWrapper<ZhilianOptionEntity>()
                        .eq("type", type)
                        .orderByAsc("sort_order")
        );
    }

    public ZhilianOptionEntity getOptionByTypeAndCode(String type, String code) {
        return zhilianOptionMapper.selectOne(
                new QueryWrapper<ZhilianOptionEntity>()
                        .eq("type", type)
                        .eq("code", code)
                        .last("LIMIT 1")
        );
    }

    public String getCodeByTypeAndName(String type, String name) {
        ZhilianOptionEntity e = zhilianOptionMapper.selectOne(
                new QueryWrapper<ZhilianOptionEntity>()
                        .eq("type", type)
                        .eq("name", name)
                        .last("LIMIT 1")
        );
        return e == null ? null : e.getCode();
    }

    public String getNameByTypeAndCode(String type, String code) {
        ZhilianOptionEntity e = getOptionByTypeAndCode(type, code);
        return e == null ? null : e.getName();
    }

    // ==================== 数据表初始化与数据操作 ====================

    @PostConstruct
    public void ensureZhilianDataTableExists() {
        String createSql = "CREATE TABLE IF NOT EXISTS zhilian_data (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " job_id VARCHAR(64)," +
                " job_title VARCHAR(200)," +
                " job_link VARCHAR(300)," +
                " salary VARCHAR(100)," +
                " location VARCHAR(100)," +
                " experience VARCHAR(100)," +
                " degree VARCHAR(100)," +
                " company_name VARCHAR(200)," +
                " delivery_status VARCHAR(20) DEFAULT '未投递'," +
                " create_time DATETIME," +
                " update_time DATETIME" +
                ")";
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createSql);
            log.info("确保 zhilian_data 表已存在");
        } catch (Exception e) {
            log.warn("创建 zhilian_data 表失败: {}", e.getMessage());
        }
    }

    public boolean existsByJobId(String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) return false;
        QueryWrapper<ZhilianJobDataEntity> w = new QueryWrapper<>();
        w.eq("job_id", jobId).last("LIMIT 1");
        Long c = zhilianJobDataMapper.selectCount(w);
        return c != null && c > 0;
    }

    public boolean existsByTitleAndCompany(String jobTitle, String companyName) {
        if (jobTitle == null || companyName == null) return false;
        QueryWrapper<ZhilianJobDataEntity> w = new QueryWrapper<>();
        w.eq("job_title", jobTitle).eq("company_name", companyName).last("LIMIT 1");
        Long c = zhilianJobDataMapper.selectCount(w);
        return c != null && c > 0;
    }

    public void insertJob(ZhilianJobDataEntity entity) {
        if (entity == null) return;
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        if (entity.getDeliveryStatus() == null) entity.setDeliveryStatus("未投递");
        zhilianJobDataMapper.insert(entity);
    }

    public void markDeliveredByJobId(String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) return;
        ZhilianJobDataEntity upd = new ZhilianJobDataEntity();
        upd.setDeliveryStatus("已投递");
        upd.setUpdateTime(LocalDateTime.now());
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ZhilianJobDataEntity> uw =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        uw.eq("job_id", jobId);
        zhilianJobDataMapper.update(upd, uw);
    }

    public void markDeliveredByTitleAndCompany(String jobTitle, String companyName) {
        if (jobTitle == null || companyName == null) return;
        ZhilianJobDataEntity upd = new ZhilianJobDataEntity();
        upd.setDeliveryStatus("已投递");
        upd.setUpdateTime(LocalDateTime.now());
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ZhilianJobDataEntity> uw =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        uw.eq("job_title", jobTitle).eq("company_name", companyName);
        zhilianJobDataMapper.update(upd, uw);
    }

    // ==================== 投递分析（Dashboard）与列表 ====================

    /** 薪资解析结果 */
    public static class SalaryInfo {
        public Integer minK;      // 最小K（单位：K/月）
        public Integer maxK;      // 最大K（单位：K/月）
        public Integer months;    // 月数（默认12）
        public Double medianK;    // 中位数K
        public Long annualTotal;  // 年包（单位：元）
    }

    /** 解析薪资字符串，支持示例：20-40K、35-65K·16薪、30K·15薪、面议（返回null） */
    public static SalaryInfo parseSalary(String salary) {
        if (salary == null) return null;
        String s = salary.trim();
        if (s.isEmpty()) return null;
        if (s.contains("面议")) return null;
        s = s.replace(" ", "");

        Integer months = 12;
        java.util.regex.Matcher mMonths = java.util.regex.Pattern.compile("[·\\.\\-]?([0-9]+)薪").matcher(s);
        if (mMonths.find()) {
            try { months = Integer.parseInt(mMonths.group(1)); } catch (Exception ignore) {}
            s = s.substring(0, mMonths.start());
        }

        Integer minK = null, maxK = null;
        java.util.regex.Matcher mRange = java.util.regex.Pattern.compile("^(\\d+)-(\\d+)[Kk]$").matcher(s);
        java.util.regex.Matcher mSingle = java.util.regex.Pattern.compile("^(\\d+)[Kk]$").matcher(s);
        if (mRange.find()) {
            try { minK = Integer.parseInt(mRange.group(1)); maxK = Integer.parseInt(mRange.group(2)); } catch (Exception ignore) {}
        } else if (mSingle.find()) {
            try { minK = Integer.parseInt(mSingle.group(1)); maxK = minK; } catch (Exception ignore) {}
        } else {
            String cleaned = s.replaceAll("[^0-9Kk\\-]", "");
            mRange = java.util.regex.Pattern.compile("^(\\d+)-(\\d+)[Kk]$").matcher(cleaned);
            mSingle = java.util.regex.Pattern.compile("^(\\d+)[Kk]$").matcher(cleaned);
            if (mRange.find()) {
                try { minK = Integer.parseInt(mRange.group(1)); maxK = Integer.parseInt(mRange.group(2)); } catch (Exception ignore) {}
            } else if (mSingle.find()) {
                try { minK = Integer.parseInt(mSingle.group(1)); maxK = minK; } catch (Exception ignore) {}
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
        public long filtered; // 智联暂不区分，置0或来源于 delivery_status
        public long failed;   // 智暂不区分，置0或来源于 delivery_status
        public Double avgMonthlyK; // 平均中位数K
    }

    /** 通用 name-value 项 */
    public static class NameValue { public String name; public long value; public NameValue(){} public NameValue(String n,long v){name=n;value=v;} }
    /** 薪资桶 */
    public static class BucketValue { public String bucket; public long value; public BucketValue(){} public BucketValue(String b,long v){bucket=b;value=v;} }

    /** 图表集合 */
    public static class Charts {
        public List<NameValue> byStatus;
        public List<NameValue> byCity;
        public List<NameValue> byCompany;
        public List<NameValue> byExperience;
        public List<NameValue> byDegree;
        public List<BucketValue> salaryBuckets;
        public List<NameValue> dailyTrend; // date 作为 name
    }

    /** 统计响应 */
    public static class StatsResponse { public Kpi kpi; public Charts charts; }

    /** 获取智联投递统计（带筛选） */
    public StatsResponse getZhilianStats(
            List<String> statuses,
            String location,
            String experience,
            String degree,
            Double minK,
            Double maxK,
            String keyword
    ) {
        QueryWrapper<ZhilianJobDataEntity> wrapper = new QueryWrapper<>();
        if (statuses != null && !statuses.isEmpty()) {
            wrapper.in("delivery_status", statuses.stream().filter(Objects::nonNull).map(String::trim).collect(Collectors.toSet()));
        }
        if (location != null && !location.trim().isEmpty()) wrapper.eq("location", location.trim());
        if (experience != null && !experience.trim().isEmpty()) wrapper.eq("experience", experience.trim());
        if (degree != null && !degree.trim().isEmpty()) wrapper.eq("degree", degree.trim());
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like("company_name", kw)
                    .or().like("job_title", kw));
        }

        wrapper.orderByDesc("create_time");
        List<ZhilianJobDataEntity> all = zhilianJobDataMapper.selectList(wrapper);

        List<ZhilianJobDataEntity> filtered = new ArrayList<>();
        for (ZhilianJobDataEntity e : all) {
            if (minK == null && maxK == null) {
                filtered.add(e);
            } else {
                SalaryInfo info = parseSalary(e.getSalary());
                if (info == null || info.medianK == null) continue;
                boolean ok = true;
                if (minK != null) ok = ok && (info.medianK >= minK);
                if (maxK != null) ok = ok && (info.medianK <= maxK);
                if (ok) filtered.add(e);
            }
        }

        Kpi kpi = new Kpi();
        kpi.total = filtered.size();
        kpi.delivered = filtered.stream().filter(e -> "已投递".equals(nullSafe(e.getDeliveryStatus()))).count();
        kpi.pending = filtered.stream().filter(e -> "未投递".equals(nullSafe(e.getDeliveryStatus()))).count();
        kpi.filtered = filtered.stream().filter(e -> "已过滤".equals(nullSafe(e.getDeliveryStatus()))).count();
        kpi.failed = filtered.stream().filter(e -> "投递失败".equals(nullSafe(e.getDeliveryStatus()))).count();
        {
            List<Double> medians = new ArrayList<>();
            for (ZhilianJobDataEntity e : filtered) {
                SalaryInfo info = parseSalary(e.getSalary());
                if (info == null || info.medianK == null) continue;
                medians.add(info.medianK);
            }
            kpi.avgMonthlyK = medians.isEmpty() ? null : medians.stream().mapToDouble(d -> d).average().orElse(0.0);
        }

        Charts charts = new Charts();
        charts.byStatus = new ArrayList<>();
        charts.byCity = new ArrayList<>();
        charts.byCompany = new ArrayList<>();
        charts.byExperience = new ArrayList<>();
        charts.byDegree = new ArrayList<>();
        charts.salaryBuckets = new ArrayList<>();
        charts.dailyTrend = new ArrayList<>();

        Map<String, Long> byStatus = filtered.stream()
                .collect(Collectors.groupingBy(e -> nullSafe(e.getDeliveryStatus()), Collectors.counting()));
        byStatus.forEach((k, v) -> charts.byStatus.add(new NameValue(k, v)));

        Map<String, Long> byCity = filtered.stream()
                .filter(e -> e.getLocation() != null && !e.getLocation().trim().isEmpty())
                .collect(Collectors.groupingBy(e -> nullSafe(e.getLocation()), Collectors.counting()));
        byCity.forEach((k, v) -> charts.byCity.add(new NameValue(k, v)));

        Map<String, Long> byCompany = filtered.stream()
                .filter(e -> e.getCompanyName() != null && !e.getCompanyName().trim().isEmpty())
                .collect(Collectors.groupingBy(e -> nullSafe(e.getCompanyName()), Collectors.counting()));
        byCompany.forEach((k, v) -> charts.byCompany.add(new NameValue(k, v)));

        Map<String, Long> byExp = filtered.stream()
                .filter(e -> e.getExperience() != null && !e.getExperience().trim().isEmpty())
                .collect(Collectors.groupingBy(e -> nullSafe(e.getExperience()), Collectors.counting()));
        byExp.forEach((k, v) -> charts.byExperience.add(new NameValue(k, v)));

        Map<String, Long> byDegree = filtered.stream()
                .filter(e -> e.getDegree() != null && !e.getDegree().trim().isEmpty())
                .collect(Collectors.groupingBy(e -> nullSafe(e.getDegree()), Collectors.counting()));
        byDegree.forEach((k, v) -> charts.byDegree.add(new NameValue(k, v)));

        // 每日趋势（按日期聚合 create_time）
        Map<String, Long> byDay = filtered.stream()
                .filter(e -> e.getCreateTime() != null)
                .collect(Collectors.groupingBy(e -> e.getCreateTime().toLocalDate().toString(), Collectors.counting()));
        byDay.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(en -> charts.dailyTrend.add(new NameValue(en.getKey(), en.getValue())));

        // 薪资桶
        long b0_10=0,b10_15=0,b15_20=0,b20_top=0,b_ge_top=0;
        double maxMedian = 0.0;
        List<Double> medians = new ArrayList<>();
        for (ZhilianJobDataEntity e : filtered) {
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

        StatsResponse resp = new StatsResponse();
        resp.kpi = kpi;
        resp.charts = charts;
        return resp;
    }

    /** 列表查询（分页 + 筛选 + 关键词 + 薪资区间基于中位数K） */
    public PagedResult listZhilianJobs(
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

        QueryWrapper<ZhilianJobDataEntity> wrapper = new QueryWrapper<>();
        if (statuses != null && !statuses.isEmpty()) {
            wrapper.in("delivery_status", statuses.stream().filter(Objects::nonNull).map(String::trim).collect(Collectors.toSet()));
        }
        if (location != null && !location.trim().isEmpty()) wrapper.eq("location", location.trim());
        if (experience != null && !experience.trim().isEmpty()) wrapper.eq("experience", experience.trim());
        if (degree != null && !degree.trim().isEmpty()) wrapper.eq("degree", degree.trim());

        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like("company_name", kw)
                    .or().like("job_title", kw));
        }

        wrapper.orderByDesc("create_time");
        List<ZhilianJobDataEntity> all = zhilianJobDataMapper.selectList(wrapper);

        List<ZhilianJobDataEntity> filtered = new ArrayList<>();
        for (ZhilianJobDataEntity e : all) {
            if (minK == null && maxK == null) {
                filtered.add(e);
            } else {
                SalaryInfo info = parseSalary(e.getSalary());
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

    public static class PagedResult {
        public List<ZhilianJobDataEntity> items;
        public long total;
        public int page;
        public int size;
    }

    private static String nullSafe(String s) { return s == null ? "" : s.trim(); }
}