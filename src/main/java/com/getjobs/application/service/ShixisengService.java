package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.getjobs.application.entity.ShixisengConfigEntity;
import com.getjobs.application.entity.ShixisengEntity;
import com.getjobs.application.mapper.ShixisengConfigMapper;
import com.getjobs.application.mapper.ShixisengMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShixisengService {

    private final ShixisengConfigMapper shixisengConfigMapper;
    private final ShixisengMapper shixisengMapper;
    private final DataSource dataSource;

    @PostConstruct
    public void ensureTablesAndSeed() {
        String createDataSql = "CREATE TABLE IF NOT EXISTS shixiseng_data (" +
                " job_id            VARCHAR(64) PRIMARY KEY," +
                " job_title         VARCHAR(200)," +
                " job_link          VARCHAR(300)," +
                " job_salary_text   VARCHAR(100)," +
                " job_area          VARCHAR(100)," +
                " job_edu_req       VARCHAR(50)," +
                " job_exp_req       VARCHAR(100)," +
                " comp_name         VARCHAR(200)," +
                " comp_industry     VARCHAR(100)," +
                " comp_scale        VARCHAR(50)," +
                " delivered         INTEGER DEFAULT 0," +
                " create_time       DATETIME," +
                " update_time       DATETIME" +
                ")";
        String createConfigSql = "CREATE TABLE IF NOT EXISTS shixiseng_config (" +
                " id                INTEGER PRIMARY KEY AUTOINCREMENT," +
                " keywords          TEXT," +
                " city              VARCHAR(100)," +
                " enable_ai         INTEGER DEFAULT 1," +
                " created_at        DATETIME," +
                " updated_at        DATETIME" +
                ")";
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createDataSql);
            stmt.execute(createConfigSql);
            log.info("确保 shixiseng_data 与 shixiseng_config 表已存在");
        } catch (Exception e) {
            log.warn("创建实习僧表失败: {}", e.getMessage());
        }

        try {
            if (shixisengConfigMapper.selectCount(null) == 0) {
                ShixisengConfigEntity seed = new ShixisengConfigEntity();
                seed.setKeywords("[\"AI产品实习生\",\"产品经理实习生\",\"产品实习生\",\"产品助理实习生\",\"产品运营实习生\"]");
                seed.setCity("广州");
                seed.setEnableAi(1);
                seed.setCreatedAt(LocalDateTime.now());
                seed.setUpdatedAt(LocalDateTime.now());
                shixisengConfigMapper.insert(seed);
                log.info("已初始化默认 shixiseng_config 记录");
            }
        } catch (Exception e) {
            log.warn("初始化 shixiseng_config 失败: {}", e.getMessage());
        }
    }

    public ShixisengConfigEntity getFirstConfig() {
        List<ShixisengConfigEntity> list = shixisengConfigMapper.selectList(new QueryWrapper<ShixisengConfigEntity>().orderByAsc("id").last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    public ShixisengConfigEntity saveOrUpdateConfig(ShixisengConfigEntity incoming) {
        LocalDateTime now = LocalDateTime.now();
        ShixisengConfigEntity existing = incoming.getId() != null
                ? shixisengConfigMapper.selectById(incoming.getId())
                : getFirstConfig();
        if (existing == null) {
            incoming.setCreatedAt(now);
            incoming.setUpdatedAt(now);
            if (incoming.getEnableAi() == null) incoming.setEnableAi(1);
            shixisengConfigMapper.insert(incoming);
            return incoming;
        }
        if (incoming.getKeywords() != null) existing.setKeywords(incoming.getKeywords());
        if (incoming.getCity() != null) existing.setCity(incoming.getCity());
        if (incoming.getEnableAi() != null) existing.setEnableAi(incoming.getEnableAi());
        existing.setUpdatedAt(now);
        shixisengConfigMapper.updateById(existing);
        return existing;
    }

    public void saveOrUpdateSnapshot(ShixisengEntity entity) {
        if (entity == null || entity.getJobId() == null) return;
        try {
            ShixisengEntity existing = shixisengMapper.selectById(entity.getJobId());
            LocalDateTime now = LocalDateTime.now();
            if (existing == null) {
                entity.setCreateTime(now);
                entity.setUpdateTime(now);
                if (entity.getDelivered() == null) entity.setDelivered(0);
                shixisengMapper.insert(entity);
            } else {
                entity.setCreateTime(existing.getCreateTime());
                entity.setUpdateTime(now);
                if (entity.getDelivered() == null) entity.setDelivered(existing.getDelivered());
                shixisengMapper.updateById(entity);
            }
        } catch (Exception e) {
            log.warn("保存实习僧岗位快照失败 job_id={}: {}", entity.getJobId(), e.getMessage());
        }
    }

    public boolean isDelivered(String jobId) {
        if (jobId == null) return false;
        ShixisengEntity existing = shixisengMapper.selectById(jobId);
        return existing != null && Integer.valueOf(1).equals(existing.getDelivered());
    }

    public void markDelivered(String jobId) {
        if (jobId == null) return;
        try {
            ShixisengEntity existing = shixisengMapper.selectById(jobId);
            if (existing != null) {
                existing.setDelivered(1);
                existing.setUpdateTime(LocalDateTime.now());
                shixisengMapper.updateById(existing);
            }
        } catch (Exception e) {
            log.warn("更新实习僧投递状态失败 job_id={}: {}", jobId, e.getMessage());
        }
    }

    public static class Kpi {
        public long total;
        public long delivered;
        public long pending;
        public long filtered;
        public long failed;
        public Double avgMonthlyK;
    }

    public static class NameValue {
        public String name;
        public long value;
        public NameValue() {}
        public NameValue(String n, long v) { name = n; value = v; }
    }

    public static class BucketValue {
        public String bucket;
        public long value;
        public BucketValue() {}
        public BucketValue(String b, long v) { bucket = b; value = v; }
    }

    public static class Charts {
        public List<NameValue> byStatus = new ArrayList<>();
        public List<NameValue> byCity = new ArrayList<>();
        public List<NameValue> byIndustry = new ArrayList<>();
        public List<NameValue> byCompany = new ArrayList<>();
        public List<NameValue> byExperience = new ArrayList<>();
        public List<NameValue> byDegree = new ArrayList<>();
        public List<BucketValue> salaryBuckets = new ArrayList<>();
        public List<NameValue> dailyTrend = new ArrayList<>();
        public List<NameValue> hrActivity = new ArrayList<>();
    }

    public static class StatsResponse {
        public Kpi kpi = new Kpi();
        public Charts charts = new Charts();
    }

    public static class PagedResult {
        public List<ShixisengEntity> items;
        public long total;
        public int page;
        public int size;
    }

    private String nullSafe(String s) { return (s == null || s.trim().isEmpty()) ? "未知" : s.trim(); }

    public StatsResponse getShixisengStats(List<String> statuses, String location, String degree, String keyword) {
        StatsResponse resp = new StatsResponse();
        List<ShixisengEntity> filtered = queryFiltered(statuses, location, degree, keyword);

        resp.kpi.total = filtered.size();
        resp.kpi.delivered = filtered.stream().filter(e -> Objects.equals(e.getDelivered(), 1)).count();
        resp.kpi.pending = filtered.stream().filter(e -> e.getDelivered() == null || Objects.equals(e.getDelivered(), 0)).count();

        filtered.stream()
                .collect(Collectors.groupingBy(e -> Objects.equals(e.getDelivered(), 1) ? "已投递" : "未投递", Collectors.counting()))
                .forEach((k, v) -> resp.charts.byStatus.add(new NameValue(k, v)));

        filtered.stream()
                .collect(Collectors.groupingBy(e -> nullSafe(e.getJobArea()), Collectors.counting()))
                .entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(10)
                .forEach(en -> resp.charts.byCity.add(new NameValue(en.getKey(), en.getValue())));

        filtered.stream()
                .collect(Collectors.groupingBy(e -> nullSafe(e.getCompIndustry()), Collectors.counting()))
                .entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(10)
                .forEach(en -> resp.charts.byIndustry.add(new NameValue(en.getKey(), en.getValue())));

        filtered.stream()
                .collect(Collectors.groupingBy(e -> nullSafe(e.getCompName()), Collectors.counting()))
                .entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(10)
                .forEach(en -> resp.charts.byCompany.add(new NameValue(en.getKey(), en.getValue())));

        filtered.stream()
                .collect(Collectors.groupingBy(e -> nullSafe(e.getJobEduReq()), Collectors.counting()))
                .entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .forEach(en -> resp.charts.byDegree.add(new NameValue(en.getKey(), en.getValue())));

        return resp;
    }

    public PagedResult listShixisengJobs(List<String> statuses, String location, String degree, String keyword, int page, int size) {
        if (page <= 0) page = 1;
        if (size <= 0) size = 20;
        List<ShixisengEntity> filtered = queryFiltered(statuses, location, degree, keyword);
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

    private List<ShixisengEntity> queryFiltered(List<String> statuses, String location, String degree, String keyword) {
        QueryWrapper<ShixisengEntity> wrapper = new QueryWrapper<>();
        if (statuses != null && !statuses.isEmpty()) {
            Set<Integer> deliveredSet = new HashSet<>();
            for (String s : statuses) {
                if ("已投递".equals(s.trim())) deliveredSet.add(1);
                if ("未投递".equals(s.trim())) deliveredSet.add(0);
            }
            if (!deliveredSet.isEmpty()) wrapper.in("delivered", deliveredSet);
        }
        if (location != null && !location.trim().isEmpty()) wrapper.eq("job_area", location.trim());
        if (degree != null && !degree.trim().isEmpty()) wrapper.eq("job_edu_req", degree.trim());
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like("comp_name", kw).or().like("job_title", kw));
        }
        wrapper.orderByDesc("create_time");
        return shixisengMapper.selectList(wrapper);
    }
}
