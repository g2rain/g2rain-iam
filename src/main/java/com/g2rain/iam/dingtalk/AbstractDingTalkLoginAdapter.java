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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 钉钉 OAuth2 授权码换票与用户信息的公共 HTTP 逻辑；子类仅提供 clientId / clientSecret 与 {@link IdpBindMode}。
 */
public abstract class AbstractDingTalkLoginAdapter implements DingTalkLoginAdapter {

    /**
     * 方式一（浏览器整页跳转）：{@code login.dingtalk.com/oauth2/auth}，{@code client_id} + {@code appid} + 新版 scope。
     */
    private static final String OAUTH_SCOPE_BROWSER = "openid Contact.User.Read";

    /**
     * 方式二（内嵌扫码 {@code DDLogin} 的 {@code goto}）：{@code oapi.../sns_authorize}，仅 {@code snsapi_login}，
     * 勿与 {@code Contact.User.Read} 混写，否则钉钉会返回 {@code goto param is invalid}。
     *
     * @see <a href="https://open.dingtalk.com/document/development/tutorial-obtaining-user-personal-information">登录第三方网站</a>
     */
    private static final String DD_LOGIN_SNS_AUTHORIZE_URL = "https://oapi.dingtalk.com/connect/oauth2/sns_authorize";

    private static final String SNS_GET_USERINFO_BY_CODE_URL = "https://oapi.dingtalk.com/sns/getuserinfo_bycode";

    private static final String OAUTH_SCOPE_SNS_QR_ONLY = "snsapi_login";

    /**
     * 方式二 sns 用户信息：{@code POST .../sns/getuserinfo_bycode} 签名原文（毫秒时间戳 + 换行 + appSecret）。
     */
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
        return UriComponentsBuilder.fromUriString(dingTalkIamProperties.getAuthorizeUrl())
            .queryParam("response_type", "code")
            .queryParam("client_id", clientId())
            .queryParam("appid", authorizeAppid())
            .queryParam("scope", OAUTH_SCOPE_BROWSER)
            .queryParam("state", state)
            .queryParam("redirect_uri", redirectUriForDingTalk)
            .queryParam("prompt", "consent")
            // build(false)：生成 URI 时对 query 编码；build(true) 会拒绝 scope 中未编码的空格（如 openid Contact.User.Read）
            .build(false)
            .toUriString();
    }

    @Override
    public String buildQrEmbeddedAuthorizeUrl(String state, String redirectUriForDingTalk) {
        requireCredentials();
        return UriComponentsBuilder.fromUriString(DD_LOGIN_SNS_AUTHORIZE_URL)
            .queryParam("appid", authorizeAppid())
            .queryParam("response_type", "code")
            .queryParam("scope", OAUTH_SCOPE_SNS_QR_ONLY)
            .queryParam("state", state)
            .queryParam("redirect_uri", redirectUriForDingTalk)
            .build(false)
            .toUriString();
    }

    /**
     * 扫码等场景钉钉端要求 {@code appid}；Nacos 未单独配置时与 {@link #clientId()} 相同。
     */
    private String authorizeAppid() {
        String configured = bindMode() == IdpBindMode.INTERNAL
            ? dingTalkIamProperties.getInternal().getAppid()
            : dingTalkIamProperties.getThirdParty().getAppid();
        if (Strings.isNotBlank(configured)) {
            return configured.trim();
        }
        return clientId() == null ? "" : clientId().trim();
    }

    @Override
    public DingTalkPrincipal exchangeCodeForPrincipal(String authCode, boolean snsQrLogin) {
        requireCredentials();
        log.info(
            "[dingtalk-oauth] exchangeCodeForPrincipal start bindMode={} snsQrLogin={} oauthClientIdPrefix={} authCodeLen={}",
            bindMode().name(),
            snsQrLogin,
            maskClientId(clientId()),
            authCode == null ? 0 : authCode.length()
        );
        DingTalkPrincipal p = snsQrLogin
            ? exchangeSnsTmpAuthCodeForPrincipal(authCode)
            : exchangeOAuthCodeForPrincipal(authCode);
        log.info(
            "[dingtalk-oauth] exchangeCodeForPrincipal done bindMode={} snsQrLogin={} subjectPresent={} nickPresent={}",
            bindMode().name(),
            snsQrLogin,
            Strings.isNotBlank(p.unionId()),
            Strings.isNotBlank(p.nick())
        );
        return p;
    }

    /** 方式一：新版 OAuth 换票 + {@code contact/users/me}。 */
    private DingTalkPrincipal exchangeOAuthCodeForPrincipal(String authCode) {
        String tokenBody = postJson(dingTalkIamProperties.getUserAccessTokenUrl(), tokenRequestBody(authCode));
        String accessToken = extractAccessToken(tokenBody);
        String userBody = getUserMe(accessToken);
        return parsePrincipal(userBody);
    }

    /**
     * 方式二：sns 临时授权码换用户信息（勿调 {@code users/me}，避免缺少 {@code Contact.User.Read}）。
     *
     * @see <a href="https://open.dingtalk.com/document/orgapp/obtain-the-user-information-based-on-the-sns-temporary-authorization">根据 sns 临时授权码获取用户信息</a>
     */
    private DingTalkPrincipal exchangeSnsTmpAuthCodeForPrincipal(String tmpAuthCode) {
        long timestamp = System.currentTimeMillis();
        String accessKey = authorizeAppid();
        String signature = computeSnsRequestSignature(timestamp, clientSecret());
        String url = SNS_GET_USERINFO_BY_CODE_URL
            + "?accessKey=" + URLEncoder.encode(accessKey, StandardCharsets.UTF_8)
            + "&timestamp=" + timestamp
            + "&signature=" + signature;
        String requestBody = snsTmpAuthCodeRequestBody(tmpAuthCode);
        String responseBody = postJsonToUrl(url, requestBody, "snsGetUserInfoByCode", IamErrorCode.DINGTALK_USERINFO_FAILED);
        return parseSnsUserInfo(responseBody);
    }

    private String snsTmpAuthCodeRequestBody(String tmpAuthCode) {
        try {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("tmp_auth_code", tmpAuthCode);
            return objectMapper.writeValueAsString(n);
        } catch (Exception e) {
            throw new BusinessException(IamErrorCode.DINGTALK_USERINFO_FAILED);
        }
    }

    /**
     * 钉钉 sns 接口签名（个人免登 / getuserinfo_bycode）：
     * Base64(HmacSHA256(appSecret, String.valueOf(timestamp))) 后再按钉钉规则 urlEncode。
     *
     * @see <a href="https://open.dingtalk.com/document/development/signature-personal-registration">个人免登场景的签名计算方法</a>
     */
    private static String computeSnsRequestSignature(long timestampMillis, String appSecret) {
        try {
            String stringToSign = Long.toString(timestampMillis);
            Mac mac = Mac.getInstance(SNS_SIGNATURE_MAC);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), SNS_SIGNATURE_MAC));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String base64 = Base64.getEncoder().encodeToString(signData);
            return urlEncodeDingTalkSignature(base64);
        } catch (Exception e) {
            throw new BusinessException(IamErrorCode.DINGTALK_USERINFO_FAILED);
        }
    }

    /**
     * 钉钉要求对 Base64 签名再做 urlEncode，且 {@code + * ~ /} 需按约定替换（勿用 UriComponentsBuilder 二次编码）。
     */
    private static String urlEncodeDingTalkSignature(String base64Signature) {
        String encoded = URLEncoder.encode(base64Signature, StandardCharsets.UTF_8);
        return encoded
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("~", "%7E")
            .replace("/", "%2F");
    }

    private String postJsonToUrl(String url, String json, String logLabel, IamErrorCode failureCode) {
        log.debug("[dingtalk-oauth] POST {} url={} bodyLen={}", logLabel, url, json == null ? 0 : json.length());
        try {
            String body = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve()
                .body(String.class);
            if (body == null) {
                body = "";
            }
            log.debug("[dingtalk-oauth] {} response len={}", logLabel, body.length());
            return body;
        } catch (RestClientResponseException e) {
            String errBody = responseExceptionBodyUtf8(e);
            log.error(
                "[dingtalk-oauth] {} HTTP {} url={} responseSnippet={}",
                logLabel,
                e.getStatusCode().value(),
                url,
                truncate(errBody, 900)
            );
            throw new BusinessException(failureCode);
        } catch (RestClientException e) {
            log.error("[dingtalk-oauth] {} request failed url={}", logLabel, url, e);
            throw new BusinessException(failureCode);
        }
    }

    private DingTalkPrincipal parseSnsUserInfo(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            if (root.has("errcode") && root.get("errcode").asInt() != 0) {
                log.warn(
                    "[dingtalk-oauth] sns getuserinfo_bycode errcode={} errmsg={} bindMode={}",
                    root.get("errcode").asInt(),
                    text(root, "errmsg"),
                    bindMode().name()
                );
                throw new BusinessException(IamErrorCode.DINGTALK_USERINFO_FAILED);
            }
            JsonNode userInfo = root.path("user_info");
            if (userInfo.isMissingNode() || userInfo.isNull()) {
                userInfo = root.path("userInfo");
            }
            String unionId = firstNonBlank(
                text(userInfo, "unionid"),
                text(userInfo, "unionId")
            );
            String openId = firstNonBlank(
                text(userInfo, "openid"),
                text(userInfo, "openId")
            );
            if (Strings.isBlank(unionId) && Strings.isBlank(openId)) {
                log.warn(
                    "[dingtalk-oauth] sns user_info missing unionid/openid bindMode={} snippet={}",
                    bindMode().name(),
                    truncate(responseJson, 600)
                );
                throw new BusinessException(IamErrorCode.DINGTALK_USERINFO_FAILED);
            }
            String stableSubject = Strings.isNotBlank(unionId) ? unionId : openId;
            String nick = text(userInfo, "nick");
            return new DingTalkPrincipal(
                stableSubject,
                openId,
                "",
                nick,
                bindMode().name(),
                responseJson,
                clientId()
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn(
                "[dingtalk-oauth] parseSnsUserInfo failed bindMode={} snippet={}",
                bindMode().name(),
                truncate(responseJson, 500),
                e
            );
            throw new BusinessException(IamErrorCode.DINGTALK_USERINFO_FAILED);
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (Strings.isNotBlank(a)) {
            return a;
        }
        return b == null ? "" : b;
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

    /**
     * 钉钉换票 / userMe 响应为 {@code application/json} 对象体。
     * 须使用 {@link com.g2rain.iam.config.DingTalkIamConfiguration#dingTalkRestClient()} 专用 {@link RestClient}
     * （无 Jackson HTTP 转换器），此处 {@code .body(String.class)} 为<strong>原始 UTF-8 文本</strong>，再由 {@link ObjectMapper} 解析业务字段。
     */
    private String postJson(String url, String json) {
        return postJsonToUrl(url, json, "userAccessToken", IamErrorCode.DINGTALK_TOKEN_EXCHANGE_FAILED);
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
            log.warn(
                "[dingtalk-oauth] token JSON missing accessToken; bindMode={} summary={}",
                bindMode().name(),
                summarizeTokenJsonForLog(tokenJson)
            );
        } catch (Exception e) {
            log.warn(
                "[dingtalk-oauth] token JSON parse failed bindMode={} snippet={}",
                bindMode().name(),
                truncate(tokenJson, 500),
                e
            );
        }
        throw new BusinessException(IamErrorCode.DINGTALK_TOKEN_EXCHANGE_FAILED);
    }

    private String getUserMe(String accessToken) {
        String url = dingTalkIamProperties.getUserMeUrl();
        log.debug("[dingtalk-oauth] GET userMe url={} accessTokenLen={}", url, accessToken == null ? 0 : accessToken.length());
        try {
            String body = restClient.get()
                .uri(url)
                .header("x-acs-dingtalk-access-token", accessToken)
                .retrieve()
                .body(String.class);
            if (body == null) {
                body = "";
            }
            log.debug("[dingtalk-oauth] userMe response len={}", body.length());
            return body;
        } catch (RestClientResponseException e) {
            String errBody = responseExceptionBodyUtf8(e);
            log.error(
                "[dingtalk-oauth] userMe HTTP {} url={} responseSnippet={}",
                e.getStatusCode().value(),
                url,
                truncate(errBody, 900)
            );
            throw new BusinessException(IamErrorCode.DINGTALK_USERINFO_FAILED);
        } catch (RestClientException e) {
            log.error("[dingtalk-oauth] userMe request failed url={}", url, e);
            throw new BusinessException(IamErrorCode.DINGTALK_USERINFO_FAILED);
        }
    }

    private DingTalkPrincipal parsePrincipal(String userJson) {
        try {
            JsonNode u = objectMapper.readTree(userJson);
            String unionId = text(u, "unionId");
            String openId = text(u, "openId");
            if (Strings.isBlank(unionId) && Strings.isBlank(openId)) {
                log.warn(
                    "[dingtalk-oauth] userMe JSON missing unionId and openId bindMode={} snippet={}",
                    bindMode().name(),
                    truncate(userJson, 600)
                );
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
            log.warn("[dingtalk-oauth] parsePrincipal failed bindMode={} userJsonSnippet={}", bindMode().name(), truncate(userJson, 500), e);
            throw new BusinessException(IamErrorCode.DINGTALK_USERINFO_FAILED);
        }
    }

    private static String text(JsonNode n, String field) {
        if (n != null && n.hasNonNull(field)) {
            return n.get(field).asText();
        }
        return "";
    }

    private static String responseExceptionBodyUtf8(RestClientResponseException e) {
        try {
            return e.getResponseBodyAsString(StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            byte[] raw = e.getResponseBodyAsByteArray();
            if (raw == null || raw.length == 0) {
                return "";
            }
            return new String(raw, StandardCharsets.UTF_8);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...(truncated)";
    }

    /** 日志用：不输出 accessToken 明文，仅结构/错误字段摘要。 */
    private String summarizeTokenJsonForLog(String tokenJson) {
        if (Strings.isBlank(tokenJson)) {
            return "(empty)";
        }
        try {
            JsonNode root = objectMapper.readTree(tokenJson);
            if (root.hasNonNull("accessToken") || root.hasNonNull("access_token")) {
                return "(success shape, token field present)";
            }
            StringBuilder sb = new StringBuilder();
            appendField(sb, root, "code");
            appendField(sb, root, "message");
            appendField(sb, root, "subCode");
            appendField(sb, root, "requestid");
            appendField(sb, root, "requestId");
            if (sb.length() > 0) {
                return sb.toString();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return truncate(tokenJson, 400);
    }

    private static void appendField(StringBuilder sb, JsonNode root, String field) {
        if (!root.hasNonNull(field)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("; ");
        }
        sb.append(field).append('=').append(truncate(root.get(field).asText(), 200));
    }

    /** OAuth ClientId 仅打前缀，避免日志过长。 */
    private static String maskClientId(String clientId) {
        if (Strings.isBlank(clientId)) {
            return "(blank)";
        }
        String t = clientId.trim();
        if (t.length() <= 10) {
            return t.substring(0, Math.min(4, t.length())) + "…";
        }
        return t.substring(0, 8) + "…(len=" + t.length() + ')';
    }
}
