package com.getjobs.worker.boss;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossBlacklistRulesTest {

    @Test
    void onlyExplicitRejectionMessagesAreDetected() {
        assertTrue(Boss.isRejectionMessage("很遗憾，您的简历未通过筛选"));
        assertTrue(Boss.isRejectionMessage("感谢投递，该岗位暂不考虑新人"));

        assertFalse(Boss.isRejectionMessage("不知道是否方便沟通"));
        assertFalse(Boss.isRejectionMessage("感谢回复，我们约时间聊聊"));
        assertFalse(Boss.isRejectionMessage("但是该岗位需要尽快到岗"));
    }

    @Test
    void blankBlacklistTermsNeverMatch() {
        Set<String> patterns = new java.util.HashSet<>();
        patterns.add("");
        patterns.add("   ");
        patterns.add(null);

        assertFalse(Boss.matchesBlacklist(patterns, "任意岗位"));
        assertFalse(Boss.matchesCompanyBlacklist(patterns, "任意公司"));
    }

    @Test
    void shortCompanyTermsRequireExactMatch() {
        Set<String> patterns = Set.of("法本");

        assertTrue(Boss.matchesCompanyBlacklist(patterns, " 法本 "));
        assertFalse(Boss.matchesCompanyBlacklist(patterns, "法本科技"));
        assertFalse(Boss.matchesCompanyBlacklist(patterns, "广州市法本科技有限公司"));
    }

    @Test
    void longerCompanyTermsStillSupportFullCompanyNames() {
        Set<String> patterns = Set.of("法本信息");

        assertTrue(Boss.matchesCompanyBlacklist(patterns, "深圳法本信息技术有限公司"));
    }
}
