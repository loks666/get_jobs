package com.getjobs.application.service;

import com.getjobs.application.entity.BlacklistEntity;
import com.getjobs.application.mapper.BlacklistMapper;
import com.getjobs.application.mapper.BossConfigMapper;
import com.getjobs.application.mapper.BossIndustryMapper;
import com.getjobs.application.mapper.BossJobDataMapper;
import com.getjobs.application.mapper.BossOptionMapper;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BossServiceBlacklistTest {

    private final BlacklistMapper blacklistMapper = mock(BlacklistMapper.class);
    private final BossService service = new BossService(
            mock(BossOptionMapper.class),
            mock(BossIndustryMapper.class),
            mock(BossConfigMapper.class),
            blacklistMapper,
            mock(BossJobDataMapper.class),
            mock(DataSource.class)
    );

    @Test
    void blacklistValuesAreTrimmedAndBlanksAreDiscarded() {
        when(blacklistMapper.selectList(any())).thenReturn(List.of(
                blacklist(" 法本信息 "), blacklist("   "), blacklist(null)
        ));

        assertEquals(java.util.Set.of("法本信息"), service.getBlacklistByType("company"));
    }

    @Test
    void blankBlacklistValueIsNotPersisted() {
        assertFalse(service.addBlacklist("company", "   "));
        assertFalse(service.addBlacklist("company", null));

        verify(blacklistMapper, never()).insert(any(BlacklistEntity.class));
    }

    private static BlacklistEntity blacklist(String value) {
        BlacklistEntity entity = new BlacklistEntity();
        entity.setValue(value);
        return entity;
    }
}
