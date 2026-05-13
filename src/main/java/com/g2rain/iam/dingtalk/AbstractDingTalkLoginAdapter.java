package com.g2rain.iam.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.config.DingTalkIamProperties;
import com.g2rain.iam.enums.IamErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 钉钉 OAuth2 授权码换票与用户信息的公共 HTTP 逻辑；子类仅提供 clientId / clientSecret 与 {@link IdpBindMode}。
 */
public abstract class AbstractDingTalkLoginAdapter implements DingTalkLoginAdapter {

    protected final DingTalkIamProperties dingTalkIamProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    protected AbstractDingTalkLoginAdapter(DingTalkIamProperties dingTalkIamProperties,
                                           @Qualifier("dingTalkRestClient") RestClient dingTalkRestClient,
                                           ObjectMapper objectMapper) {
        this.dingTalkIamProperties = dingTalkIamProperties;
        this.restClient = dingTalkRestClient;
        this.objectMapper = objectMapper;
    }

    protected abstract String clientId();

    protected abstract String clientSecret();

    @Override
    public abstract IdpBindMode bindMode();

    @Override
    public String buildAuthorizeUrl(String state, String redirectUriForDingTalk) {
        requireCredentials();
        return UriComponentsBuilder.fromUriString(dingTalkIamProperties.getAuthorizeUrl())
            .queryParam("response_type", "code")
            .queryParam("client_id", clientId())
            .queryParam("scope", "openid")
            .queryParam("state", state)
            .queryParam("redirect_uri", redirectUriForDingTalk)
            .queryParam("prompt", "consent")
            .build(true)
            .toUriString();
    }

    @Override
    public DingTalkPrincipal exchangeCodeForPrincipal(String authCode) {
        requireCredentials();
        String tokenBody = postJson(dingTalkIamProperties.getUserAccessTokenUrl(), tokenRequestBody(authCode));
        String accessToken = extractAccessToken(tokenBody);
        String userBody = getUserMe(accessToken);
        return parsePrincipal(userBody);
    }

    private void requireCredentials() {
        if (Strings.isBlank(clientId()) || Strings.isBlank(clientSecret())) {
            throw new BusinessException(IamErrorCode.DINGTALK_TOKEN_EXCHANGE_FAILED);
        }
    }

    private String tokenRequestBody(String authCode) {
        try {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("clientId", clientId());
            n.put("clientSecret", clientSecret());
            n.put("code", authCode);
            n.put("grantType", "authorization_code");
            return objectMapper.writeValueAsString(n);
        } catch (Exception e) {
            throw new BusinessException(IamErrorCode.DINGTALK_TOKEN_EXCHANGE_FAILED);
        }
    }

    private String postJson(String url, String json) {
        try {
            return restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve()
                .body(String.class);
        } catch (RestClientResponseException e) {
            throw new BusinessException(IamErrorCode.DINGTALK_TOKEN_EXCHANGE_FAILED);
        }
    }

    private String extractAccessToken(String tokenJson) {
        try {
            JsonNode root = objectMapper.readTree(tokenJson);
            if (root.hasNonNull("accessToken")) {
                return root.get("accessToken").asText();
            }
            if (root.hasNonNull("access_token")) {
                return root.get("access_token").asText();
            }
        } catch (Exception ignored) {
            // fall through
        }
        throw new BusinessException(IamErrorCode.DINGTALK_TOKEN_EXCHANGE_FAILED);
    }

    private String getUserMe(String accessToken) {
        try {
            return restClient.get()
                .uri(dingTalkIamProperties.getUserMeUrl())
                .header("x-acs-dingtalk-access-token", accessToken)
                .retrieve()
                .body(String.class);
        } catch (RestClientResponseException e) {
            throw new BusinessException(IamErrorCode.DINGTALK_USERINFO_FAILED);
        }
    }

    private DingTalkPrincipal parsePrincipal(String userJson) {
        try {
            JsonNode u = objectMapper.readTree(userJson);
            String unionId = text(u, "unionId");
            String openId = text(u, "openId");
            if (Strings.isBlank(unionId) && Strings.isBlank(openId)) {
                throw new BusinessException(IamErrorCode.DINGTALK_USERINFO_FAILED);
            }
            String stableSubject = Strings.isNotBlank(unionId) ? unionId : openId;
            String corpId = text(u, "corpId");
            String nick = text(u, "nick");
            if (Strings.isBlank(nick)) {
                nick = text(u, "name");
            }
            return new DingTalkPrincipal(stableSubject, openId, corpId, nick, bindMode().name(), userJson, clientId());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(IamErrorCode.DINGTALK_USERINFO_FAILED);
        }
    }

    private static String text(JsonNode n, String field) {
        if (n != null && n.hasNonNull(field)) {
            return n.get(field).asText();
        }
        return "";
    }
}
