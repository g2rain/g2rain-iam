package com.g2rain.iam.service;

import com.g2rain.common.utils.Strings;
import com.g2rain.iam.config.IamAccessProperties;
import com.g2rain.iam.utils.Constants;
import com.g2rain.iam.utils.IamUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 授权流程 Cookie：{@link Constants#AUTH_FLOW_COOKIE_NAME}，Path=/auth，SameSite=Lax。
 */
@Service
@RequiredArgsConstructor
public class AuthFlowCookieService {

    private final IamAccessProperties iamAccessProperties;

    public String readRaw(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (Constants.AUTH_FLOW_COOKIE_NAME.equals(cookie.getName()) && Strings.isNotBlank(cookie.getValue())) {
                return cookie.getValue().trim();
            }
        }
        return null;
    }

    public String hash(String raw) {
        if (Strings.isBlank(raw)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * 有合法 Cookie 则复用；否则签发新值并写入响应。返回用于绑定事务的哈希。
     */
    public String ensureAndBind(HttpServletRequest request, HttpServletResponse response) {
        String existing = readRaw(request);
        if (Strings.isNotBlank(existing)) {
            return hash(existing);
        }
        String raw = IamUtils.generateSessionId();
        writeCookie(response, raw, iamAccessProperties.getAuthorizationTransaction().getTtlSeconds());
        return hash(raw);
    }

    public void clear(HttpServletResponse response) {
        writeCookie(response, "", 0);
    }

    private void writeCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
        boolean secure = iamAccessProperties.resolveSessionCookieSecure();
        ResponseCookie cookie = ResponseCookie.from(Constants.AUTH_FLOW_COOKIE_NAME, value == null ? "" : value)
            .httpOnly(true)
            .secure(secure)
            .path("/auth")
            .maxAge(maxAgeSeconds)
            .sameSite("Lax")
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
