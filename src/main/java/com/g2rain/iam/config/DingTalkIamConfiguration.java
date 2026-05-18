package com.g2rain.iam.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g2rain.common.utils.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
            log.info(
                "[iam-startup] iam baseUrl={} platformBaseUrl={} dingtalk callback={} loginPageBindMode={} sessionCookieSecure={} sessionCookieSameSite={}",
                nullToEmpty(iamAccess.getBaseUrl()),
                iamAccess.resolvedPlatformBaseUrl(),
                dingTalk.fullCallbackUrl(iamAccess.normalizedBaseUrl()),
                Strings.isBlank(dingTalk.getLoginPageBindMode()) ? "(hidden)" : dingTalk.getLoginPageBindMode().trim(),
                iamAccess.resolveSessionCookieSecure(),
                iamAccess.resolveSessionCookieSameSite()
            );
            logCredential("internal", dingTalk.getInternal());
            logCredential("third-party", dingTalk.getThirdParty());
        };
    }

    private static void logCredential(String label, DingTalkIamProperties.Credential c) {
        String cid = c.getClientId() == null ? "" : c.getClientId().trim();
        log.info("[iam-startup] dingtalk.{} clientId={} clientSecret={}",
            label,
            Strings.isBlank(cid) ? "(not set)" : cid,
            maskSecret(c.getClientSecret()));
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

    /**
     * 钉钉 OpenAPI 响应为 {@code application/json} 对象体；使用全局 {@link RestClient.Builder} 时，
     * Jackson 会参与 {@code String}/{@code byte[]} 反序列化导致非数组/非字符串根节点报错。
     * 此处使用<strong>不含 Jackson</strong>的转换器列表，按字节流读写 JSON 文本。
     */
    @Bean
    public RestClient dingTalkRestClient() {
        StringHttpMessageConverter strings = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        List<MediaType> dingTalkReadableWritable = new ArrayList<>();
        dingTalkReadableWritable.add(MediaType.TEXT_PLAIN);
        dingTalkReadableWritable.add(MediaType.TEXT_HTML);
        dingTalkReadableWritable.add(MediaType.APPLICATION_JSON);
        dingTalkReadableWritable.add(MediaType.APPLICATION_FORM_URLENCODED);
        strings.setSupportedMediaTypes(dingTalkReadableWritable);

        return RestClient.builder()
            .messageConverters(converters -> {
                converters.clear();
                converters.add(strings);
                converters.add(new ByteArrayHttpMessageConverter());
                converters.add(new ResourceHttpMessageConverter());
            })
            .build();
    }
}
