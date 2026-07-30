package com.getjobs.worker.manager;

import com.microsoft.playwright.options.Cookie;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BossCookieImportTest {
    @Test
    void acceptsCookieEditorJsonAndRequestCookieHeader() {
        List<Cookie> jsonCookies = PlaywrightManager.parseBossCookies("""
                [{"name":"wt2","value":"token","domain":".zhipin.com","path":"/",
                  "expirationDate":1893456000,"sameSite":"unspecified"}]
                """);
        List<Cookie> headerCookies = PlaywrightManager.parseBossCookies(
                "wt2=token; zp_at=access=token"
        );

        assertEquals(List.of("wt2"), jsonCookies.stream().map(cookie -> cookie.name).toList());
        assertEquals(1893456000, jsonCookies.get(0).expires);
        assertEquals(List.of("wt2", "zp_at"), headerCookies.stream().map(cookie -> cookie.name).toList());
        assertEquals(".zhipin.com", headerCookies.get(0).domain);
        assertEquals("access=token", headerCookies.get(1).value);
    }
}
