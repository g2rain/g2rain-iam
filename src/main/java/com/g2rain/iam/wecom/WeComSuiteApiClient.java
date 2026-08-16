package com.g2rain.iam.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.RedisKeyRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

/**
 * 企业微信服务商 Suite / Provider API 客户端（安装授权、Token 缓存）
 */
@Service
public class WeComSuiteApiClient {

    private static final Logger log = LoggerFactory.getLogger(WeComSuiteApiClient.class);

    private final WeComIamProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final GenericRedisHelper redis;

    public WeComSuiteApiClient(
        WeComIamProperties properties,
        @Qualifier("weComRestClient") RestClient restClient,
        ObjectMapper objectMapper,
        GenericRedisHelper redis
    ) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.redis = redis;
    }

    public String suiteAccessToken() {
        WeComIamProperties.ThirdParty config = properties.getThirdParty();
        String cacheKey =
            RedisKeyRule.WECOM_SUITE_ACCESS_TOKEN.format(config.getSuiteId());
        String cached = redis.get(cacheKey, String.class);
        if (Strings.isNotBlank(cached)) {
            return cached;
        }
        String ticket = redis.get(
            RedisKeyRule.WECOM_SUITE_TICKET.format(config.getSuiteId()), String.class);
        if (Strings.isBlank(ticket)) {
            throw new BusinessException(IamErrorCode.WECOM_AUTHORIZATION_EXCHANGE_FAILED);
        }
        ObjectNode request = objectMapper.createObjectNode();
        request.put("suite_id", config.getSuiteId());
        request.put("suite_secret", config.getSuiteSecret());
        request.put("suite_ticket", ticket);
        JsonNode response = postJson(WeComQyApiSupport.API + "/service/get_suite_token", request,
            IamErrorCode.WECOM_AUTHORIZATION_EXCHANGE_FAILED);
        String token = WeComQyApiSupport.requiredText(response, "suite_access_token",
            IamErrorCode.WECOM_AUTHORIZATION_EXCHANGE_FAILED);
        cacheToken(cacheKey, token, response.path("expires_in").asLong(7200));
        return token;
    }

    public JsonNode permanentCode(String authCode) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("auth_code", authCode);
        String url = UriComponentsBuilder
            .fromUriString(WeComQyApiSupport.API + "/service/get_permanent_code")
            .queryParam("suite_access_token", suiteAccessToken())
            .build(false).toUriString();
        return postJson(url, request, IamErrorCode.WECOM_AUTHORIZATION_EXCHANGE_FAILED);
    }

    public String providerAccessToken() {
        WeComIamProperties.ThirdParty config = properties.getThirdParty();
        String cacheKey = RedisKeyRule.WECOM_PROVIDER_ACCESS_TOKEN
            .format(config.getProviderCorpId());
        String cached = redis.get(cacheKey, String.class);
        if (Strings.isNotBlank(cached)) {
            return cached;
        }
        ObjectNode request = objectMapper.createObjectNode();
        request.put("corpid", config.getProviderCorpId());
        request.put("provider_secret", config.getProviderSecret());
        JsonNode response = postJson(WeComQyApiSupport.API + "/service/get_provider_token",
            request, IamErrorCode.WECOM_TOKEN_EXCHANGE_FAILED);
        String token = WeComQyApiSupport.requiredText(response, "provider_access_token",
            IamErrorCode.WECOM_TOKEN_EXCHANGE_FAILED);
        cacheToken(cacheKey, token, response.path("expires_in").asLong(7200));
        return token;
    }

    private void cacheToken(String key, String token, long expiresIn) {
        redis.set(key, token, Duration.ofSeconds(Math.max(60, expiresIn - 120)));
    }

    private JsonNode postJson(String url, JsonNode request, IamErrorCode failure) {
        return WeComQyApiSupport.postJson(log, restClient, objectMapper, url, request, failure);
    }
}
