package com.g2rain.iam.service;

import com.g2rain.iam.config.IamAccessProperties;
import com.g2rain.iam.utils.Constants;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * 统一写入 / 清除 IAM 会话 Cookie（HttpOnly、Secure、SameSite）。
 */
@Service
@RequiredArgsConstructor
public class IamSessionCookieService {

    private final IamAccessProperties iamAccessProperties;

    public void writeSessionCookie(HttpServletResponse response, String sessionId) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(sessionId, iamAccessProperties.getSessionCookie().getMaxAgeSeconds()).toString());
    }

    public void clearSessionCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", 0).toString());
    }

    private ResponseCookie buildCookie(String value, long maxAgeSeconds) {
        boolean secure = iamAccessProperties.resolveSessionCookieSecure();
        String sameSite = iamAccessProperties.resolveSessionCookieSameSite();
        if ("None".equals(sameSite) && !secure) {
            secure = true;
        }
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(Constants.SESSION_NAME, value == null ? "" : value)
            .httpOnly(true)
            .secure(secure)
            .path("/")
            .maxAge(maxAgeSeconds);
        if (sameSite != null) {
            builder.sameSite(sameSite);
        }
        return builder.build();
    }
}
