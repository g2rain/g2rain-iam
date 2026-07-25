package com.g2rain.iam.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.enums.IamErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 企业微信服务商三方应用换票适配器
 */
@Component
public class ThirdPartyWeComLoginAdapter extends AbstractWeComLoginAdapter {

    private static final String AUTHORIZE_URL =
        "https://open.work.weixin.qq.com/wwopen/sso/3rd_qrConnect";

    private final WeComSuiteApiClient suiteApiClient;

    public ThirdPartyWeComLoginAdapter(
        WeComIamProperties properties,
        @Qualifier("weComRestClient") RestClient restClient,
        ObjectMapper objectMapper,
        WeComSuiteApiClient suiteApiClient
    ) {
        super(properties, restClient, objectMapper);
        this.suiteApiClient = suiteApiClient;
    }

    @Override
    public IdpBindMode bindMode() {
        return IdpBindMode.THIRD_PARTY;
    }

    @Override
    public String buildAuthorizeUrl(String state, String callbackUrl) {
        requireThirdPartyCredentials();
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
            .queryParam("appid", properties.getThirdParty().getProviderCorpId().trim())
            .queryParam("redirect_uri", callbackUrl)
            .queryParam("state", state)
            .queryParam("usertype", "member")
            .build(false).toUriString();
    }

    @Override
    public WeComPrincipal exchangeCodeForPrincipal(String authCode) {
        requireNonBlankAuthCode(authCode);
        requireThirdPartyCredentials();
        ObjectNode request = objectMapper().createObjectNode()
            .put("auth_code", authCode.trim());
        JsonNode response = postJson(
            UriComponentsBuilder.fromUriString(WeComQyApiSupport.API + "/service/get_login_info")
                .queryParam("access_token", suiteApiClient.providerAccessToken())
                .build(false).toUriString(),
            request,
            IamErrorCode.WECOM_USERINFO_FAILED
        );
        JsonNode user = response.path("user_info");
        JsonNode corp = response.path("corp_info");
        String corpId = textAny(corp, "corpid", "corpId");
        String userId = textAny(user, "userid", "userId");
        String openUserId = textAny(user, "open_userid", "openUserId");
        if (Strings.isBlank(corpId)
            || (Strings.isBlank(userId) && Strings.isBlank(openUserId))) {
            throw new BusinessException(IamErrorCode.WECOM_USERINFO_FAILED);
        }
        String agentId = installedAgentId(response);
        return new WeComPrincipal(
            corpId,
            Strings.isNotBlank(userId) ? userId : openUserId,
            openUserId,
            textAny(user, "name"),
            IdpBindMode.THIRD_PARTY.name(),
            properties.getThirdParty().getSuiteId().trim(),
            agentId,
            response.toString()
        );
    }

    private void requireThirdPartyCredentials() {
        WeComIamProperties.ThirdParty config = properties.getThirdParty();
        if (Strings.isBlank(config.getProviderCorpId())
            || Strings.isBlank(config.getProviderSecret())
            || Strings.isBlank(config.getSuiteId())) {
            throw new BusinessException(IamErrorCode.WECOM_CREDENTIAL_MISSING);
        }
    }

    private static String installedAgentId(JsonNode response) {
        String direct = textAny(response, "agentid", "agentId");
        if (Strings.isNotBlank(direct)) {
            return direct;
        }
        JsonNode agents = response.path("agent");
        if (agents.isArray() && !agents.isEmpty()) {
            return textAny(agents.get(0), "agentid", "agentId");
        }
        return "";
    }
}
