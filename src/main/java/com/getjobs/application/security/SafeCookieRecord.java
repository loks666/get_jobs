package com.getjobs.application.security;

import com.getjobs.application.entity.CookieEntity;

import java.util.HashMap;
import java.util.Map;

public final class SafeCookieRecord {
    private SafeCookieRecord() {}

    public static Map<String, Object> fromEntity(CookieEntity cookie, String platform, String missingMessage) {
        Map<String, Object> data = new HashMap<>();
        if (cookie != null) {
            String cookieValue = cookie.getCookieValue();
            data.put("id", cookie.getId());
            data.put("platform", cookie.getPlatform());
            data.put("cookie_present", cookieValue != null && !cookieValue.isBlank());
            data.put("cookie_length", cookieValue == null ? 0 : cookieValue.length());
            data.put("remark", cookie.getRemark());
            data.put("created_at", cookie.getCreatedAt());
            data.put("updated_at", cookie.getUpdatedAt());
        } else {
            data.put("platform", platform);
            data.put("cookie_present", false);
            data.put("cookie_length", 0);
            data.put("message", missingMessage);
        }
        return data;
    }
}
