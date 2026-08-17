package com.g2rain.iam.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.enums.IamErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestClient;

/**
 * 企业微信登录换票适配器抽象实现，封装公共 HTTP 逻辑
 */
public abstract class AbstractWeComLoginAdapter implements WeComLoginAdapter {

    private static final Logger log = LoggerFactory.getLogger(AbstractWeComLoginAdapter.class);

    protected final WeComIamProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    protected AbstractWeComLoginAdapter(
        WeComIamProperties properties,
        @Qualifier("weComRestClient") RestClient restClient,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    protected JsonNode getJson(String url, IamErrorCode failure) {
        return WeComQyApiSupport.getJson(log, restClient, objectMapper, url, failure);
    }

    protected JsonNode postJson(String url, JsonNode request, IamErrorCode failure) {
        return WeComQyApiSupport.postJson(log, restClient, objectMapper, url, request, failure);
    }

    protected static String textAny(JsonNode node, String... fields) {
        return WeComQyApiSupport.textAny(node, fields);
    }

    protected static String requiredText(JsonNode node, String field, IamErrorCode failure) {
        return WeComQyApiSupport.requiredText(node, field, failure);
    }

    protected void requireNonBlankAuthCode(String authCode) {
        if (Strings.isBlank(authCode)) {
            throw new BusinessException(IamErrorCode.WECOM_TOKEN_EXCHANGE_FAILED);
        }
    }

    protected ObjectMapper objectMapper() {
        return objectMapper;
    }
}
