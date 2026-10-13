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
 * 授权流程 Cookie 服务。
 * <p>
 * Cookie 名 {@link Constants#AUTH_FLOW_COOKIE_NAME}，Path=/auth，HttpOnly，SameSite=Lax。
 * 用于将授权事务 {@code tid} 绑定到当前浏览器，防止复制 tid 到其他浏览器继续。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AuthFlowCookieService {

    private final IamAccessProperties iamAccessProperties;

    /**
     * 读取请求中的 flow Cookie 原始值。
     *
     * @param request 当前 HTTP 请求
     * @return Cookie 原始值，不存在时返回 {@code null}
     */
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

    /**
     * 对 flow Cookie 原始值做 SHA-256，得到写入事务的绑定哈希。
     *
     * @param raw Cookie 原始值
     * @return 十六进制哈希；raw 为空时返回 {@code null}
     */
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

    /**
     * 清除 flow Cookie（退出时调用）。
     *
     * @param response 当前 HTTP 响应
     */
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
