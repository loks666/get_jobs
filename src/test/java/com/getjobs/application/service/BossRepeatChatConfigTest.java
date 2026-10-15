package com.getjobs.application.service;

import com.getjobs.application.entity.BossConfigEntity;
import com.getjobs.application.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BossRepeatChatConfigTest {
    @TempDir Path directory;

    @Test
    void migratesOldDatabaseWithoutLosingSettingsAndKeepsEnabledValueOnRestart() throws Exception {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("test.db"));
        BossService service = new BossService(null, null, null, null, null, source);
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE boss_config (id INTEGER PRIMARY KEY, say_hi TEXT)");
            statement.execute("INSERT INTO boss_config VALUES (1, '原有招呼语')");
            service.ensureRepeatChatColumn();
            try (var rows = statement.executeQuery("SELECT say_hi, allow_repeat_chat FROM boss_config")) {
                assertTrue(rows.next());
                assertEquals("原有招呼语", rows.getString(1));
                assertEquals(0, rows.getInt(2));
            }
            statement.execute("UPDATE boss_config SET allow_repeat_chat = 1");
            service.ensureRepeatChatColumn();
            try (var rows = statement.executeQuery("SELECT allow_repeat_chat FROM boss_config")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
        }
    }

    @Test
    void selectiveSaveAcceptsBothSwitchValuesAndPreservesOmittedValue() {
        BossConfigMapper mapper = mock(BossConfigMapper.class);
        BossConfigEntity existing = new BossConfigEntity();
        existing.setId(1L);
        existing.setAllowRepeatChat(0);
        when(mapper.selectOne(any())).thenReturn(existing);
        BossService service = new BossService(null, null, mapper, null, null, null);
        BossConfigEntity patch = new BossConfigEntity();
        patch.setAllowRepeatChat(1);
        assertEquals(1, service.saveOrUpdateFirstSelective(patch).getAllowRepeatChat());
        assertEquals(1, service.saveOrUpdateFirstSelective(new BossConfigEntity()).getAllowRepeatChat());
        patch.setAllowRepeatChat(0);
        assertEquals(0, service.saveOrUpdateFirstSelective(patch).getAllowRepeatChat());
    }
}
