package com.getjobs.application.init;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.getjobs.application.entity.ZhilianOptionEntity;
import com.getjobs.application.mapper.ZhilianOptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
 

/**
 * 应用启动时：
 * 1) 创建 zhilian_option 表（若不存在）
 * 2) 从 city.json 解析城市码并插入（若不存在）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZhilianOptionInitializer implements CommandLineRunner {

    private final DataSource dataSource;
    private final ZhilianOptionMapper zhilianOptionMapper;

    @Override
    public void run(String... args) throws Exception {
        ensureTableExists();
        // 已改为从数据库获取城市选项，移除基于 city.json 的种子插入逻辑
    }

    private void ensureTableExists() {
        String ddl = "CREATE TABLE IF NOT EXISTS zhilian_option (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " type VARCHAR(50)," +
                " name VARCHAR(100)," +
                " code VARCHAR(100)," +
                " sort_order INTEGER," +
                " created_at DATETIME," +
                " updated_at DATETIME" +
                ")";
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
            log.info("确保 zhilian_option 表已存在");
        } catch (Exception e) {
            log.warn("创建 zhilian_option 表失败: {}", e.getMessage());
        }
    }

    // 已移除基于 city.json 的种子插入方法

    private void insertIfNotExists(String type, String name, String code, int sortOrder) {
        try {
            ZhilianOptionEntity existing = zhilianOptionMapper.selectOne(
                    new QueryWrapper<ZhilianOptionEntity>()
                            .eq("type", type)
                            .eq("code", code)
                            .last("LIMIT 1")
            );
            if (existing != null) return;

            LocalDateTime now = LocalDateTime.now();
            ZhilianOptionEntity e = new ZhilianOptionEntity();
            e.setType(type);
            e.setName(name);
            e.setCode(code);
            e.setSortOrder(sortOrder);
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            zhilianOptionMapper.insert(e);
        } catch (Exception ex) {
            log.warn("插入城市选项失败 code={}: {}", code, ex.getMessage());
        }
    }

    // 已移除 CityItem 记录类型
}