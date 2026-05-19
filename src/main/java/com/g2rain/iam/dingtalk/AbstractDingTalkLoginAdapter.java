package com.g2rain.iam.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.config.DingTalkIamProperties;
import com.g2rain.iam.enums.IamErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 钉钉登录换票适配器抽象实现
 * 功能：封装授权 URL 构造、OAuth 换票与 SNS 扫码换票公共 HTTP 逻辑；子类提供 clientId / clientSecret 与 {@link IdpBindMode}
 *
 * @author Alpha
 */
public abstract class AbstractDingTalkLoginAdapter implements DingTalkLoginAdapter {

    private static final String OAUTH_SCOPE_BROWSER = "openid Contact.User.Read";
    private static final String DD_LOGIN_SNS_AUTHORIZE_URL = "https://oapi.dingtalk.com/connect/oauth2/sns_authorize";
    private static final String SNS_GET_USERINFO_BY_CODE_URL = "https://oapi.dingtalk.com/sns/getuserinfo_bycode";
    private static final String OAUTH_SCOPE_SNS_QR_ONLY = "snsapi_login";
    private static final String SNS_SIGNATURE_MAC = "HmacSHA256";

    private static final Logger log = LoggerFactory.getLogger(AbstractDingTalkLoginAdapter.class);

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
        return sharedAuthorizeBuilder(dingTalkIamProperties.getAuthorizeUrl(), state, redirectUriForDingTalk)
            .queryParam("client_id", appKey())
            .queryParam("scope", OAUTH_SCOPE_BROWSER)
            .queryParam("prompt", "consent")
            .build(false)
            .toUriString();
    }

    @Override
    public String buildQrEmbeddedAuthorizeUrl(String state, String redirectUriForDingTalk) {
        requireCredentials();
        return sharedAuthorizeBuilder(DD_LOGIN_SNS_AUTHORIZE_URL, state, redirectUriForDingTalk)
            .queryParam("scope", OAUTH_SCOPE_SNS_QR_ONLY)
            .build(false)
            .toUriString();
    }

    @Override
    public DingTalkPrincipal exchangeCodeForPrincipal(String authCode, boolean snsQrLogin) {
        requireCredentials();
        return snsQrLogin
            ? exchangeSnsTmpAuthCodeForPrincipal(authCode)
            : exchangeOAuthCodeForPrincipal(authCode);
    }

    private UriComponentsBuilder sharedAuthorizeBuilder(String baseUrl, String state, String redirectUri) {
        return UriComponentsBuilder.fromUriString(baseUrl)
            .queryParam("response_type", "code")
            .queryParam("appid", appKey())
            .queryParam("state", state)
            .queryParam("redirect_uri", redirectUri);
    }

    private DingTalkPrincipal exchangeOAuthCodeForPrincipal(String authCode) {
        String tokenBody = postJson(
            dingTalkIamProperties.getUserAccessTokenUrl(),
            jsonBody(
                b -> {
                    b.put("clientId", appKey());
                    b.put("clientSecret", appSecret());
                    b.put("code", authCode);
                    b.put("grantType", "authorization_code");
                },
                IamErrorCode.DINGTALK_TOKEN_EXCHANGE_FAILED
            ),
            IamErrorCode.DINGTALK_TOKEN_EXCHANGE_FAILED
        );
        String userBody = getJson(
            dingTalkIamProperties.getUserMeUrl(),
            "x-acs-dingtalk-access-token",
            extractAccessToken(tokenBody),
            IamErrorCode.DINGTALK_USERINFO_FAILED
        );
        return parseOAuthUserInfo(userBody);
    }

    private DingTalkPrincipal exchangeSnsTmpAuthCodeForPrincipal(String tmpAuthCode) {
        long timestamp = System.currentTimeMillis();
        String url = snsGetUserInfoByCodeUrl(appKey(), appSecret(), timestamp);
        String responseBody = postJson(
            url,
            jsonBody(b -> b.put("tmp_auth_code", tmpAuthCode), IamErrorCode.DINGTALK_USERINFO_FAILED),
            IamErrorCode.DINGTALK_USERINFO_FAILED
        );
        return parseSnsUserInfo(responseBody);
    }

    private String snsGetUserInfoByCodeUrl(String accessKey, String appSecret, long timestamp) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("accessKey", accessKey);
        query.put("signature", computeSnsSignatureBase64(String.valueOf(timestamp), appSecret));
        query.put("timestamp", String.valueOf(timestamp));
        return SNS_GET_USERINFO_BY_CODE_URL + '?' + snsQueryString(query);
    }

    private static String computeSnsSignatureBase64(String canonical, String appSecret) {
        try {
            Mac mac = Mac.getInstance(SNS_SIGNATURE_MAC);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), SNS_SIGNATURE_MAC));
            byte[] signData = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signData);
        } catch (Exception e) {
            throw new BusinessException(IamErrorCode.DINGTALK_USERINFO_FAILED);
        }
    }

    private static String snsQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            sb.append(urlEncodeDingTalk(e.getKey())).append('=').append(urlEncodeDingTalk(e.getValue()));
            first = false;
        }
        return sb.toString();
    }

    private static String urlEncodeDingTalk(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("~", "%7E")
            .replace("/", "%2F");
    }

    private String jsonBody(Consumer<ObjectNode> fields, IamErrorCode failureCode) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            fields.accept(node);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new BusinessException(failureCode);
        }
    }

    private String postJson(String url, String body, IamErrorCode failureCode) {
        try {
            String response = restClient.post()
                .uri(URI.create(url))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
            return response == null ? "" : response;
        } catch (RestClientResponseException e) {
            log.error(
                "[dingtalk-oauth] POST failed bindMode={} status={} snippet={}",
                bindMode().name(),
                e.getStatusCode().value(),
                truncate(responseBodyUtf8(e), 500)
            );
            throw new BusinessException(failureCode);
        } catch (RestClientException e) {
            log.error("[dingtalk-oauth] POST failed bindMode={}", bindMode().name(), e);
            throw new BusinessException(failureCode);
        }
    }

    private String getJson(String url, String headerName, String headerValue, IamErrorCode failureCode) {
        try {
            String response = restClient.get()
                .uri(url)
                .header(headerName, headerValue)
                .retrieve()
                .body(String.class);
            return response == null ? "" : response;
        } catch (RestClientResponseException e) {
            log.error(
                "[dingtalk-oauth] GET failed bindMode={} status={} snippet={}",
                bindMode().name(),
                e.getStatusCode().value(),
                truncate(responseBodyUtf8(e), 500)
            );
            throw new BusinessException(failureCode);
        } catch (RestClientException e) {
            log.error("[dingtalk-oauth] GET failed bindMode={}", bindMode().name(), e);
            throw new BusinessException(failureCode);
        }
    }

    private DingTalkPrincipal parseOAuthUserInfo(String userJson) {
        try {
            JsonNode u = objectMapper.readTree(userJson);
            String unionId = textAny(u, "unionId", "unionid");
            String openId = textAny(u, "openId", "openid");
            String nick = textAny(u, "nick", "name");
            return buildPrincipal(unionId, openId, text(u, "corpId"), nick, userJson);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[dingtalk-oauth] parse OAuth user failed bindMode={}", bindMode().name(), e);
            throw new BusinessException(IamErrorCode.DINGTALK_USERINFO_FAILED);
        }
    }

    private DingTalkPrincipal parseSnsUserInfo(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            assertSnsOk(root);
            JsonNode user = root.path("user_info");
            if (user.isMissingNode() || user.isNull()) {
                user = root.path("userInfo");
            }
            return buildPrincipal(
                textAny(user, "unionid", "unionId"),
                textAny(user, "openid", "openId"),
                "",
                text(user, "nick"),
                responseJson
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[dingtalk-oauth] parse sns user failed bindMode={}", bindMode().name(), e);
            throw new BusinessException(IamErrorCode.DINGTALK_USERINFO_FAILED);
        }
    }

    private void assertSnsOk(JsonNode root) {
        if (!root.has("errcode") || root.get("errcode").asInt() == 0) {
            return;
        }
        log.warn(
            "[dingtalk-oauth] sns errcode={} errmsg={} bindMode={}",
            root.get("errcode").asInt(),
            text(root, "errmsg"),
            bindMode().name()
        );
        throw new BusinessException(IamErrorCode.DINGTALK_USERINFO_FAILED);
    }

    private DingTalkPrincipal buildPrincipal(String unionId, String openId, String corpId, String nick, String rawJson) {
        if (Strings.isBlank(unionId) && Strings.isBlank(openId)) {
            log.warn("[dingtalk-oauth] missing unionId/openId bindMode={}", bindMode().name());
            throw new BusinessException(IamErrorCode.DINGTALK_USERINFO_FAILED);
        }
        String subject = Strings.isNotBlank(unionId) ? unionId : openId;
        return new DingTalkPrincipal(
            subject,
            openId,
            corpId == null ? "" : corpId,
            nick,
            bindMode().name(),
            rawJson,
            clientId()
        );
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
            log.warn("[dingtalk-oauth] token response missing accessToken bindMode={}", bindMode().name());
        } catch (Exception e) {
            log.warn("[dingtalk-oauth] token response parse failed bindMode={}", bindMode().name(), e);
        }
        throw new BusinessException(IamErrorCode.DINGTALK_TOKEN_EXCHANGE_FAILED);
    }

    private void requireCredentials() {
        if (Strings.isBlank(appKey()) || Strings.isBlank(appSecret())) {
            throw new BusinessException(IamErrorCode.DINGTALK_TOKEN_EXCHANGE_FAILED);
        }
    }

    private String appKey() {
        return clientId() == null ? "" : clientId().trim();
    }

    private String appSecret() {
        return clientSecret() == null ? "" : clientSecret().trim();
    }

    private static String text(JsonNode node, String field) {
        if (node != null && node.hasNonNull(field)) {
            return node.get(field).asText();
        }
        return "";
    }

    private static String textAny(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (Strings.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private static String responseBodyUtf8(RestClientResponseException e) {
        try {
            return e.getResponseBodyAsString(StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            byte[] raw = e.getResponseBodyAsByteArray();
            return raw == null || raw.length == 0 ? "" : new String(raw, StandardCharsets.UTF_8);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
