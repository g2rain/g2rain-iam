package com.g2rain.iam.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.enums.IamErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 钉钉企业管理员断言（租户开通 loginRole=ADMIN）。
 */
@Slf4j
@Component
public class DingTalkAdminAsserter {

    private static final String GET_BY_UNION_ID =
        "https://oapi.dingtalk.com/topapi/user/getbyunionid";
    private static final String GET_USER =
        "https://oapi.dingtalk.com/topapi/v2/user/get";

    private final DingTalkContactClient dingTalkContactClient;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public DingTalkAdminAsserter(
        DingTalkContactClient dingTalkContactClient,
        ObjectMapper objectMapper,
        @Qualifier("dingTalkRestClient") RestClient restClient
    ) {
        this.dingTalkContactClient = dingTalkContactClient;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    public void assertCorpAdmin(String bindMode, String corpId, String unionId, String idpApplicationCode) {
        if (Strings.isBlank(unionId)) {
            throw new BusinessException(IamErrorCode.IDP_ADMIN_ASSERTION_FAILED);
        }
        IdpBindMode mode = IdpBindMode.valueOf(bindMode);
        String accessToken = dingTalkContactClient.resolveAccessToken(
            mode, idpApplicationCode, corpId);
        String userId = resolveUserIdByUnionId(accessToken, unionId.trim());
        if (Strings.isBlank(userId)) {
            throw new BusinessException(IamErrorCode.IDP_ADMIN_ASSERTION_FAILED);
        }
        if (!isAdmin(accessToken, userId)) {
            throw new BusinessException(IamErrorCode.IDP_ADMIN_ASSERTION_FAILED);
        }
    }

    private String resolveUserIdByUnionId(String accessToken, String unionId) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("unionid", unionId);
            JsonNode response = restClient.post()
                .uri(GET_BY_UNION_ID + "?access_token={token}", accessToken)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
            if (response == null || response.path("errcode").asInt(-1) != 0) {
                log.warn("[dingtalk-admin] getbyunionid failed errcode={}",
                    response == null ? null : response.path("errcode").asInt());
                return null;
            }
            return text(response.path("result"), "userid");
        } catch (Exception e) {
            log.warn("[dingtalk-admin] getbyunionid error", e);
            return null;
        }
    }

    private boolean isAdmin(String accessToken, String userId) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("userid", userId);
            body.put("language", "zh_CN");
            JsonNode response = restClient.post()
                .uri(GET_USER + "?access_token={token}", accessToken)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
            if (response == null || response.path("errcode").asInt(-1) != 0) {
                return false;
            }
            JsonNode result = response.path("result");
            if (result.path("admin").asBoolean(false)) {
                return true;
            }
            return result.path("boss").asBoolean(false);
        } catch (Exception e) {
            log.warn("[dingtalk-admin] get user error", e);
            return false;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }
}
