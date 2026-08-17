package com.g2rain.iam.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.enums.IamErrorCode;
import org.slf4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;

/**
 * 企业微信 qyapi 公共 HTTP 与 JSON 解析工具
 */
final class WeComQyApiSupport {

    static final String API = "https://qyapi.weixin.qq.com/cgi-bin";

    private WeComQyApiSupport() {
    }

    static JsonNode getJson(
        Logger log,
        RestClient restClient,
        ObjectMapper objectMapper,
        String url,
        IamErrorCode failure
    ) {
        try {
            String body = restClient.get().uri(URI.create(url)).retrieve()
                .body(String.class);
            return parseAndValidate(log, objectMapper, body, failure);
        } catch (RestClientException exception) {
            log.warn("[wecom] GET request failed", exception);
            throw new BusinessException(failure);
        }
    }

    static JsonNode postJson(
        Logger log,
        RestClient restClient,
        ObjectMapper objectMapper,
        String url,
        JsonNode request,
        IamErrorCode failure
    ) {
        try {
            String body = restClient.post().uri(URI.create(url))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve().body(String.class);
            return parseAndValidate(log, objectMapper, body, failure);
        } catch (RestClientException exception) {
            log.warn("[wecom] POST request failed", exception);
            throw new BusinessException(failure);
        }
    }

    static JsonNode parseAndValidate(
        Logger log,
        ObjectMapper objectMapper,
        String body,
        IamErrorCode failure
    ) {
        try {
            JsonNode response = objectMapper.readTree(body == null ? "" : body);
            if (response.path("errcode").asInt(0) != 0) {
                log.warn("[wecom] API errcode={} errmsg={}",
                    response.path("errcode").asInt(), response.path("errmsg").asText());
                throw new BusinessException(failure);
            }
            return response;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(failure);
        }
    }

    static String requiredText(JsonNode node, String field, IamErrorCode failure) {
        String value = textAny(node, field);
        if (Strings.isBlank(value)) {
            throw new BusinessException(failure);
        }
        return value;
    }

    static String textAny(JsonNode node, String... fields) {
        if (node == null) {
            return "";
        }
        for (String field : fields) {
            if (node.hasNonNull(field)) {
                String value = node.get(field).asText();
                if (Strings.isNotBlank(value)) {
                    return value.trim();
                }
            }
        }
        return "";
    }
}
