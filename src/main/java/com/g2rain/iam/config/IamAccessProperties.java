package com.g2rain.iam.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IAM 自身对外可访问地址（部署时由环境变量 / Nacos 注入），用于拼 OAuth 回调、文档链接等。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "g2rain.iam")
public class IamAccessProperties {

    /**
     * IAM 对外根 URL（协议 + 主机 + 可选端口），无尾斜杠。
     * <p>示例：{@code https://43.138.13.145} 或 {@code https://iam.example.com}</p>
     */
    private String accessBaseUrl = "http://127.0.0.1:8082";

    /**
     * 业务平台（控制台）对外根 URL，无尾斜杠；用于首页等跳转到非 IAM 宿主上的路径（如 {@code /main/home}）。
     * <p>为空时由调用方回退使用 {@link #accessBaseUrl}。</p>
     */
    private String platformBaseUrl = "";

    /**
     * 控制台 / 平台对外根 URL（已 trim、去尾斜杠），用于拼接 {@code /main/home} 等。
     * <p>{@link #platformBaseUrl} 非空白则用之，否则回退 {@link #accessBaseUrl}。</p>
     */
    public String resolvedPlatformBaseUrl() {
        String chosen = (platformBaseUrl != null && !platformBaseUrl.isBlank()) ? platformBaseUrl : accessBaseUrl;
        if (chosen == null) {
            chosen = "";
        }
        String base = chosen.trim();
        while (!base.isEmpty() && base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
