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
 * 钉钉 IAM 配置类
 * 功能：注册钉钉换票所需的 RestClient、ObjectMapper 等 Bean
 *
 * @author Alpha
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({DingTalkIamProperties.class, IamAccessProperties.class})
public class DingTalkIamConfiguration {

    /**
     * 启动后打印 IAM / 钉钉生效配置（secret 脱敏）
     *
     * @param dingTalk  钉钉配置属性
     * @param iamAccess IAM 访问配置属性
     * @return 启动回调
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
     * 脱敏 secret，仅提示是否已配置及长度
     */
    private static String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "(not set)";
        }
        return "(set, " + secret.length() + " chars)";
    }

    /**
     * 钉钉换票 JSON 解析用 ObjectMapper
     *
     * @return ObjectMapper 实例
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * 钉钉 OpenAPI 专用 RestClient（不含 Jackson，按字节流读写 JSON 文本）
     *
     * @return RestClient 实例
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
