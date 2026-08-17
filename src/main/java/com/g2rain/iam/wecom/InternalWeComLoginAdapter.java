package com.g2rain.iam.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.enums.IamErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 企业微信企业内部自建应用换票适配器
 */
@Component
public class InternalWeComLoginAdapter extends AbstractWeComLoginAdapter {

    private static final Logger log = LoggerFactory.getLogger(InternalWeComLoginAdapter.class);
    private static final String AUTHORIZE_URL =
        "https://open.work.weixin.qq.com/wwopen/sso/qrConnect";

    public InternalWeComLoginAdapter(
        WeComIamProperties properties,
        @Qualifier("weComRestClient") RestClient restClient,
        ObjectMapper objectMapper
    ) {
        super(properties, restClient, objectMapper);
    }

    @Override
    public IdpBindMode bindMode() {
        return IdpBindMode.INTERNAL;
    }

    @Override
    public String buildAuthorizeUrl(String state, String callbackUrl) {
        requireInternalCredentials();
        WeComIamProperties.Internal config = properties.getInternal();
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
            .queryParam("appid", config.getCorpId().trim())
            .queryParam("agentid", config.getAgentId().trim())
            .queryParam("redirect_uri", callbackUrl)
            .queryParam("state", state)
            .build(false).toUriString();
    }

    @Override
    public WeComPrincipal exchangeCodeForPrincipal(String authCode) {
        requireNonBlankAuthCode(authCode);
        requireInternalCredentials();
        WeComIamProperties.Internal config = properties.getInternal();
        String tokenUrl = UriComponentsBuilder.fromUriString(WeComQyApiSupport.API + "/gettoken")
            .queryParam("corpid", config.getCorpId())
            .queryParam("corpsecret", config.getSecret())
            .build(false).toUriString();
        JsonNode tokenResponse = getJson(tokenUrl, IamErrorCode.WECOM_TOKEN_EXCHANGE_FAILED);
        String token = requiredText(tokenResponse, "access_token",
            IamErrorCode.WECOM_TOKEN_EXCHANGE_FAILED);
        String infoUrl = UriComponentsBuilder
            .fromUriString(WeComQyApiSupport.API + "/user/getuserinfo")
            .queryParam("access_token", token)
            .queryParam("code", authCode.trim())
            .build(false).toUriString();
        JsonNode info = getJson(infoUrl, IamErrorCode.WECOM_USERINFO_FAILED);
        String userId = textAny(info, "UserId", "userid");
        if (Strings.isBlank(userId)) {
            throw new BusinessException(IamErrorCode.WECOM_USERINFO_FAILED);
        }
        String name = "";
        try {
            String userUrl = UriComponentsBuilder.fromUriString(WeComQyApiSupport.API + "/user/get")
                .queryParam("access_token", token)
                .queryParam("userid", userId)
                .build(false).toUriString();
            name = textAny(getJson(userUrl, IamErrorCode.WECOM_USERINFO_FAILED), "name");
        } catch (BusinessException exception) {
            log.debug("[wecom] optional internal profile lookup failed");
        }
        return new WeComPrincipal(
            config.getCorpId().trim(),
            userId,
            textAny(info, "OpenId", "openid"),
            name,
            IdpBindMode.INTERNAL.name(),
            config.getAgentId().trim(),
            config.getAgentId().trim(),
            info.toString()
        );
    }

    private void requireInternalCredentials() {
        WeComIamProperties.Internal config = properties.getInternal();
        if (Strings.isBlank(config.getCorpId())
            || Strings.isBlank(config.getAgentId())
            || Strings.isBlank(config.getSecret())) {
            throw new BusinessException(IamErrorCode.WECOM_CREDENTIAL_MISSING);
        }
    }
}
