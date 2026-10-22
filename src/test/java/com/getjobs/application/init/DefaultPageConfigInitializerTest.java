package com.getjobs.application.init;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultPageConfigInitializerTest {
    @TempDir Path directory;
    private JdbcTemplate jdbc;
    private DefaultPageConfigInitializer initializer;

    @BeforeEach
    void setup() throws Exception {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("test.db"));
        jdbc = new JdbcTemplate(source);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, List<Map<String, Object>>> defaults;
        try (var input = new ClassPathResource("defaults/page-config.json").getInputStream()) {
            defaults = mapper.readValue(input, new TypeReference<>() {});
        }
        for (var table : defaults.entrySet()) {
            String columns = String.join(",", table.getValue().getFirst().keySet());
            jdbc.execute("CREATE TABLE " + table.getKey() + " (id INTEGER PRIMARY KEY," + columns + ")");
        }
        jdbc.execute("CREATE TABLE cookie (value TEXT)");
        jdbc.update("INSERT INTO cookie VALUES ('existing-session')");
        initializer = new DefaultPageConfigInitializer(jdbc, mapper);
    }

    @Test
    void seedsFreshDatabaseWithoutSessionsAndDoesNotRepeatOnRestart() throws Exception {
        initializer.run();
        assertEquals("上海", jdbc.queryForObject("SELECT city_code FROM boss_config", String.class));
        assertTrue(jdbc.queryForObject("SELECT keywords FROM boss_config", String.class).contains("大模型"));
        assertEquals(16, jdbc.queryForObject("SELECT COUNT(*) FROM boss_blacklist", Integer.class));
        assertEquals(5, jdbc.queryForObject("SELECT prompt FROM ai", String.class).split("%s", -1).length - 1);
        assertEquals("existing-session", jdbc.queryForObject("SELECT value FROM cookie", String.class));
        jdbc.update("UPDATE boss_config SET city_code='用户修改的城市'");
        jdbc.update("DELETE FROM boss_blacklist");
        initializer.run();
        assertEquals("用户修改的城市", jdbc.queryForObject("SELECT city_code FROM boss_config", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM boss_blacklist", Integer.class));
    }

    @Test
    void upgradesPreserveExistingSettingsAndEmptyBlacklist() throws Exception {
        jdbc.update("INSERT INTO ai (introduce,prompt) VALUES ('自己的技能','自己的提示词')");
        initializer.run();
        assertEquals("自己的提示词", jdbc.queryForObject("SELECT prompt FROM ai", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM boss_config", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM boss_blacklist", Integer.class));
    }
}
