package com.g2rain.iam.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.enums.IamErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 企业内部应用管理员断言（loginRole=ADMIN）。
 */
@Slf4j
@Component
public class WeComInternalAdminAsserter {

    private final WeComIamProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WeComInternalAdminAsserter(
        WeComIamProperties properties,
        @Qualifier("weComRestClient") RestClient restClient,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public void assertCorpAdmin(String userId) {
        if (Strings.isBlank(userId)) {
            throw new BusinessException(IamErrorCode.IDP_ADMIN_ASSERTION_FAILED);
        }
        WeComIamProperties.Internal config = properties.getInternal();
        if (Strings.isBlank(config.getCorpId()) || Strings.isBlank(config.getSecret())) {
            throw new BusinessException(IamErrorCode.WECOM_CREDENTIAL_MISSING);
        }
        try {
            String tokenUrl = UriComponentsBuilder.fromUriString(WeComQyApiSupport.API + "/gettoken")
                .queryParam("corpid", config.getCorpId())
                .queryParam("corpsecret", config.getSecret())
                .build(false).toUriString();
            JsonNode tokenResponse = restClient.get().uri(tokenUrl).retrieve().body(JsonNode.class);
            String token = tokenResponse == null ? null : tokenResponse.path("access_token").asText(null);
            if (Strings.isBlank(token)) {
                throw new BusinessException(IamErrorCode.IDP_ADMIN_ASSERTION_FAILED);
            }
            String listUrl = UriComponentsBuilder
                .fromUriString(WeComQyApiSupport.API + "/agent/get_admin_list")
                .queryParam("access_token", token)
                .build(false).toUriString();
            JsonNode body = objectMapper.createObjectNode()
                .put("agentid", Integer.parseInt(config.getAgentId().trim()));
            JsonNode response = restClient.post().uri(listUrl).body(body).retrieve().body(JsonNode.class);
            if (response == null || response.path("errcode").asInt(-1) != 0) {
                // 回退：user/get 的 isleader 不足以证明超管，直接失败
                log.warn("[wecom-admin] get_admin_list failed, reject ADMIN login");
                throw new BusinessException(IamErrorCode.IDP_ADMIN_ASSERTION_FAILED);
            }
            JsonNode admins = response.path("admin");
            if (!admins.isArray()) {
                throw new BusinessException(IamErrorCode.IDP_ADMIN_ASSERTION_FAILED);
            }
            String target = userId.trim();
            for (JsonNode admin : admins) {
                String id = text(admin, "userid");
                if (target.equals(id)) {
                    return;
                }
            }
            throw new BusinessException(IamErrorCode.IDP_ADMIN_ASSERTION_FAILED);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[wecom-admin] assert failed", e);
            throw new BusinessException(IamErrorCode.IDP_ADMIN_ASSERTION_FAILED);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }
}
