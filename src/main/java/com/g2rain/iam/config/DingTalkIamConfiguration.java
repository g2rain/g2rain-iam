package com.g2rain.iam.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g2rain.common.utils.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 钉钉 IAM 相关 Bean。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({DingTalkIamProperties.class, IamAccessProperties.class})
public class DingTalkIamConfiguration {

    /**
     * 启动完成后打印当前生效的 IAM / 钉钉配置（secret 脱敏），便于对照 Nacos 与钉钉开放平台。
     */
    @Bean
    public ApplicationRunner logIamDingTalkStartupProperties(
        DingTalkIamProperties dingTalk,
        IamAccessProperties iamAccess
    ) {
        return args -> {
            log.info("[iam-startup] g2rain.iam.access-base-url={}", nullToEmpty(iamAccess.getAccessBaseUrl()));
            log.info("[iam-startup] g2rain.iam.platform-base-url={} (resolved console base={})",
                nullToEmpty(iamAccess.getPlatformBaseUrl()),
                iamAccess.resolvedPlatformBaseUrl());
            log.info("[iam-startup] g2rain.iam.dingtalk.callback-base-url={}", nullToEmpty(dingTalk.getCallbackBaseUrl()));
            log.info("[iam-startup] g2rain.iam.dingtalk.callback-path={}", nullToEmpty(dingTalk.getCallbackPath()));
            log.info("[iam-startup] g2rain.iam.dingtalk fullCallbackUrl={}", dingTalk.fullCallbackUrl());
            log.info("[iam-startup] g2rain.iam.dingtalk.login-page-bind-mode={}",
                Strings.isBlank(dingTalk.getLoginPageBindMode()) ? "(empty, login page hides DingTalk)" : dingTalk.getLoginPageBindMode().trim());
            log.info("[iam-startup] g2rain.iam.dingtalk.authorize-url={}", nullToEmpty(dingTalk.getAuthorizeUrl()));
            log.info("[iam-startup] g2rain.iam.dingtalk.user-access-token-url={}", nullToEmpty(dingTalk.getUserAccessTokenUrl()));
            log.info("[iam-startup] g2rain.iam.dingtalk.user-me-url={}", nullToEmpty(dingTalk.getUserMeUrl()));
            logCredential("internal", dingTalk.getInternal());
            logCredential("third-party", dingTalk.getThirdParty());
        };
    }

    private static void logCredential(String label, DingTalkIamProperties.Credential c) {
        String cid = c.getClientId() == null ? "" : c.getClientId().trim();
        log.info("[iam-startup] g2rain.iam.dingtalk.{}.client-id={}",
            label, Strings.isBlank(cid) ? "(not set)" : cid);
        log.info("[iam-startup] g2rain.iam.dingtalk.{}.client-secret={}", label, maskSecret(c.getClientSecret()));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * 不落库明文，仅提示是否已配置及长度。
     */
    private static String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "(not set)";
        }
        return "(set, " + secret.length() + " chars)";
    }

    /**
     * Spring Boot 4 默认未暴露 {@link ObjectMapper} Bean，钉钉换票 JSON 解析依赖显式注册。
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public RestClient dingTalkRestClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder.build();
    }
}
