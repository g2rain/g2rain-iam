package com.g2rain.iam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.g2rain.basis.dto.IdpEnterpriseApplicationAuthorizationRevokeDto;
import com.g2rain.basis.dto.IdpEnterpriseApplicationAuthorizationUpsertDto;
import com.g2rain.basis.enums.IdpApplicationAuthorizationStatus;
import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.basis.enums.IdpType;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Strings;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.client.IdpEnterpriseApplicationAuthorizationClient;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.RedisKeyRule;
import com.g2rain.iam.wecom.WeComCallbackCrypto;
import com.g2rain.iam.wecom.WeComCredentialCipher;
import com.g2rain.iam.wecom.WeComSuiteApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 企业微信第三方应用授权事件处理：suite_ticket、安装/变更授权与取消授权。
 */
@Service
@RequiredArgsConstructor
public class WeComAuthorizationService {
    private final WeComIamProperties properties;
    private final GenericRedisHelper redis;
    private final WeComSuiteApiClient suiteApiClient;
    private final WeComCredentialCipher credentialCipher;
    private final IdpEnterpriseApplicationAuthorizationClient authorizationClient;

    /**
     * 处理已解密的授权事件 XML（suite_ticket / create_auth / change_auth / cancel_auth）。
     *
     * @param xml 解密后的明文 XML
     */
    public void handleDecryptedEvent(String xml) {
        String infoType = WeComCallbackCrypto.xmlValue(xml, "InfoType");
        switch (infoType) {
            case "suite_ticket" -> {
                String ticket = WeComCallbackCrypto.xmlValue(xml, "SuiteTicket");
                if (Strings.isBlank(ticket)) {
                    throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID);
                }
                redis.set(
                    RedisKeyRule.WECOM_SUITE_TICKET.format(
                        properties.getThirdParty().getSuiteId()),
                    ticket,
                    Duration.ofMinutes(30)
                );
                redis.delete(RedisKeyRule.WECOM_SUITE_ACCESS_TOKEN.format(
                    properties.getThirdParty().getSuiteId()));
                return;
            }
            case "create_auth", "change_auth" -> {
                activate(WeComCallbackCrypto.xmlValue(xml, "AuthCode"));
                return;
            }
            case "cancel_auth" -> revoke(WeComCallbackCrypto.xmlValue(xml, "AuthCorpId"));
        }
    }

    /**
     * 使用 auth_code 换取 permanent_code，并向 Basis 写入 ACTIVE 授权记录。
     *
     * @param authCode 企业微信安装/变更授权码
     */
    public void activate(String authCode) {
        if (Strings.isBlank(authCode)) {
            throw new BusinessException(
                IamErrorCode.WECOM_AUTHORIZATION_EXCHANGE_FAILED);
        }
        JsonNode response = suiteApiClient.permanentCode(authCode.trim());
        String permanentCode = text(response, "permanent_code");
        String corpId = text(response.path("auth_corp_info"), "corpid");
        String agentId = firstAgentId(response.path("auth_info").path("agent"));
        if (Strings.isBlank(permanentCode)
            || Strings.isBlank(corpId) || Strings.isBlank(agentId)) {
            throw new BusinessException(
                IamErrorCode.WECOM_AUTHORIZATION_EXCHANGE_FAILED);
        }
        IdpEnterpriseApplicationAuthorizationUpsertDto dto =
            new IdpEnterpriseApplicationAuthorizationUpsertDto();
        dto.setIdpType(IdpType.WECHAT_WORK.name());
        dto.setBindMode(IdpBindMode.THIRD_PARTY.name());
        dto.setIdpApplicationCode(properties.getThirdParty().getSuiteId());
        dto.setEnterpriseId(corpId);
        dto.setInstalledApplicationId(agentId);
        dto.setAuthorizationStatus(
            IdpApplicationAuthorizationStatus.ACTIVE.name());
        dto.setCredentialCiphertext(credentialCipher.encrypt(permanentCode));
        dto.setCredentialKeyId(properties.getCredential().getKeyId());
        dto.setAuthorizedAt(LocalDateTime.now());
        ObjectNode sanitized = response.deepCopy();
        sanitized.remove("permanent_code");
        dto.setRawAuthorization(sanitized.toString());
        Result<Long> result = authorizationClient.upsert(dto);
        if (!result.isSuccess()) {
            throw ExceptionConverter.of(result);
        }
    }

    /**
     * 取消企业授权：通知 Basis 撤销对应企业的第三方应用授权。
     *
     * @param corpId 企业 corpId
     */
    public void revoke(String corpId) {
        if (Strings.isBlank(corpId)) {
            throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID);
        }
        IdpEnterpriseApplicationAuthorizationRevokeDto dto =
            new IdpEnterpriseApplicationAuthorizationRevokeDto();
        dto.setIdpType(IdpType.WECHAT_WORK.name());
        dto.setIdpApplicationCode(properties.getThirdParty().getSuiteId());
        dto.setEnterpriseId(corpId.trim());
        dto.setRevokedAt(LocalDateTime.now());
        Result<Integer> result = authorizationClient.revoke(dto);
        if (!result.isSuccess()) {
            throw ExceptionConverter.of(result);
        }
    }

    private static String firstAgentId(JsonNode agents) {
        if (!agents.isArray() || agents.isEmpty()) {
            return "";
        }
        return text(agents.get(0), "agentid");
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field)
            ? node.get(field).asText().trim() : "";
    }
}
