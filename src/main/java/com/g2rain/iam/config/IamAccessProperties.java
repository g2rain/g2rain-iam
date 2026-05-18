package com.g2rain.iam.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.Locale;

/**
 * IAM 自身对外可访问地址（部署时由环境变量 / Nacos 注入），用于拼 OAuth 回调、文档链接等。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "g2rain.iam")
public class IamAccessProperties {

    /**
     * IAM 对外根 URL（协议 + 主机 + 可选端口），无尾斜杠。
     * <p>配置项 {@code g2rain.iam.base-url}；示例：{@code https://43.138.13.145}</p>
     */
    private String baseUrl = "http://127.0.0.1:8082";

    /**
     * 业务平台（控制台）对外根 URL，无尾斜杠；用于首页等跳转到非 IAM 宿主上的路径（如 {@code /main/home}）。
     * <p>为空时由调用方回退使用 {@link #baseUrl}。</p>
     */
    private String platformBaseUrl = "";

    /**
     * 登录会话 Cookie（{@code G2RAIN_AUTH_SESSION_ID}）安全属性。
     */
    @NestedConfigurationProperty
    private final SessionCookie sessionCookie = new SessionCookie();

    /**
     * {@link #baseUrl} 已 trim、去尾斜杠。
     */
    public String normalizedBaseUrl() {
        return trimTrailingSlash(baseUrl);
    }

    /**
     * 控制台 / 平台对外根 URL（已 trim、去尾斜杠），用于拼接 {@code /main/home} 等。
     * <p>{@link #platformBaseUrl} 非空白则用之，否则回退 {@link #baseUrl}。</p>
     */
    public String resolvedPlatformBaseUrl() {
        String chosen = (platformBaseUrl != null && !platformBaseUrl.isBlank()) ? platformBaseUrl : baseUrl;
        return trimTrailingSlash(chosen);
    }

    /**
     * 是否对会话 Cookie 设置 {@code Secure}：显式配置优先；否则当 {@link #baseUrl} 为 {@code https} 时为 {@code true}。
     */
    public boolean resolveSessionCookieSecure() {
        if (sessionCookie.getSecure() != null) {
            return sessionCookie.getSecure();
        }
        String base = baseUrl == null ? "" : baseUrl.trim().toLowerCase(Locale.ROOT);
        return base.startsWith("https://");
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String base = url.trim();
        while (!base.isEmpty() && base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    /**
     * SameSite 策略：{@code Lax} / {@code Strict} / {@code None}，非法值回退 {@code Lax}。
     */
    public String resolveSessionCookieSameSite() {
        String raw = sessionCookie.getSameSite();
        if (raw == null || raw.isBlank()) {
            return "Lax";
        }
        String normalized = raw.trim();
        if ("Lax".equalsIgnoreCase(normalized)) {
            return "Lax";
        }
        if ("Strict".equalsIgnoreCase(normalized)) {
            return "Strict";
        }
        if ("None".equalsIgnoreCase(normalized)) {
            return "None";
        }
        return "Lax";
    }

    @Getter
    @Setter
    public static class SessionCookie {
        /**
         * 是否仅 HTTPS 传输；{@code null} 时根据 {@link IamAccessProperties#baseUrl} 是否为 https 推断。
         */
        private Boolean secure;

        /**
         * SameSite：默认 {@code Lax}（OAuth 重定向登录兼容较好）。
         */
        private String sameSite = "Lax";

        /**
         * 会话 Cookie 有效期（秒），默认 24 小时。
         */
        private int maxAgeSeconds = 24 * 60 * 60;
    }
}
