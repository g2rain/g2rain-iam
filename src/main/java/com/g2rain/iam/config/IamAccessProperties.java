package com.g2rain.iam.config;

import com.g2rain.iam.utils.IamUrlUtils;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import com.g2rain.common.utils.Collections;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
     * 匿名 OAuth 授权（{@code state=anonymous}）配置。
     */
    @NestedConfigurationProperty
    private final AnonymousAuth anonymous = new AnonymousAuth();

    /**
     * {@link #baseUrl} 已 trim、去尾斜杠。
     */
    public String normalizedBaseUrl() {
        return IamUrlUtils.trimTrailingSlash(baseUrl);
    }

    /**
     * 控制台 / 平台对外根 URL（已 trim、去尾斜杠），用于拼接 {@code /main/home} 等。
     * <p>{@link #platformBaseUrl} 非空白则用之，否则回退 {@link #baseUrl}。</p>
     */
    public String resolvedPlatformBaseUrl() {
        String chosen = (platformBaseUrl != null && !platformBaseUrl.isBlank()) ? platformBaseUrl : baseUrl;
        return IamUrlUtils.trimTrailingSlash(chosen);
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

    /**
     * SameSite 策略：{@code None}（跨域携带 Cookie）/ {@code Lax} / {@code Strict}；
     * 配置为空白时不写入 SameSite 属性；非法值回退 {@code None}。
     *
     * @return {@code null} 表示不设置 SameSite
     */
    public String resolveSessionCookieSameSite() {
        String raw = sessionCookie.getSameSite();
        if (raw == null || raw.isBlank()) {
            return null;
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
        return "None";
    }

    @Getter
    @Setter
    public static class SessionCookie {
        /**
         * 是否仅 HTTPS 传输；{@code null} 时根据 {@link IamAccessProperties#baseUrl} 是否为 https 推断。
         */
        private Boolean secure;

        /**
         * SameSite：默认 {@code None}，便于 IAM 与业务前端跨域携带会话 Cookie（须 HTTPS + Secure）。
         */
        private String sameSite = "None";

        /**
         * 会话 Cookie 有效期（秒），默认 24 小时。
         */
        private int maxAgeSeconds = 24 * 60 * 60;
    }

    @Getter
    @Setter
    public static class AnonymousAuth {

        /**
         * 是否启用 {@code state=anonymous} 匿名授权发码。
         */
        private boolean enabled = false;

        /**
         * 匿名会话所属机构 ID。
         */
        private Long organId;

        /**
         * 匿名会话绑定的角色 ID 列表。
         */
        private List<Long> roleIds = new ArrayList<>();

        /**
         * 配置是否可用于匿名发码：已启用且 organId、roleIds 非空。
         */
        public boolean isConfigured() {
            return enabled
                && Objects.nonNull(organId)
                && Collections.isNotEmpty(roleIds);
        }
    }
}
