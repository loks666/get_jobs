package com.getjobs.application.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.UnknownHostException;

public final class LocalRequestGuard {
    private LocalRequestGuard() {}

    public static void assertLoopback(HttpServletRequest request) {
        if (isLoopback(request)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该敏感接口仅允许本机访问");
    }

    private static boolean isLoopback(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String candidate = forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",")[0].trim();
        try {
            return InetAddress.getByName(candidate).isLoopbackAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
