package com.g2rain.iam.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.config.DingTalkIamProperties;
import com.g2rain.iam.enums.IamErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 钉钉通讯录 OpenAPI 客户端。
 */
@Component
public class DingTalkContactClient {

    private static final Logger log = LoggerFactory.getLogger(DingTalkContactClient.class);
    private static final String ACCESS_TOKEN_URL = "https://api.dingtalk.com/v1.0/oauth2/accessToken";
    private static final String LIST_SUB_DEPT_URL = "https://oapi.dingtalk.com/topapi/v2/department/listsub";
    private static final String LIST_USER_URL = "https://oapi.dingtalk.com/topapi/v2/user/list";
    private static final String GET_USER_URL = "https://oapi.dingtalk.com/topapi/v2/user/get";
    private static final long ROOT_DEPT_ID = 1L;

    private final DingTalkIamProperties dingTalkIamProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();
    private final DingTalkTopApiRateLimiter rateLimiter;

    private int apiCallCount;
    private int retryCount;

    public DingTalkContactClient(
        DingTalkIamProperties dingTalkIamProperties,
        @Qualifier("dingTalkRestClient") RestClient dingTalkRestClient,
        ObjectMapper objectMapper
    ) {
        this.dingTalkIamProperties = dingTalkIamProperties;
        this.restClient = dingTalkRestClient;
        this.objectMapper = objectMapper;
        this.rateLimiter = new DingTalkTopApiRateLimiter(contactSync().getQps());
    }

    public record DepartmentInfo(long deptId, long parentId, String name, int order) {
    }

    public record UserSummary(
        String userId,
        String unionId,
        String name,
        String mobile,
        String email,
        List<Long> deptIds
    ) {
    }

    public record UserDetail(String userId, String unionId, String name, String mobile, String email) {
    }

    public record SyncMetrics(int apiCallCount, int retryCount) {
    }

    public void resetSyncMetrics() {
        apiCallCount = 0;
        retryCount = 0;
    }

    public SyncMetrics syncMetrics() {
        return new SyncMetrics(apiCallCount, retryCount);
    }

    public List<DepartmentInfo> listAllDepartments(String accessToken) {
        List<DepartmentInfo> result = new ArrayList<>();
        collectDepartments(accessToken, ROOT_DEPT_ID, result);
        return result;
    }

    public List<UserSummary> listDepartmentUsers(String accessToken, long deptId) {
        List<UserSummary> users = new ArrayList<>();
        long cursor = 0L;
        boolean hasMore = true;
        int pageSize = contactSync().getUserPageSize();
        while (hasMore) {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("dept_id", deptId);
            body.put("cursor", cursor);
            body.put("size", pageSize);
            JsonNode response = postTopApi(LIST_USER_URL, accessToken, body);
            JsonNode result = response.path("result");
            JsonNode list = result.path("list");
            if (list.isArray()) {
                for (JsonNode item : list) {
                    users.add(parseUserSummary(item));
                }
            }
            hasMore = result.path("has_more").asBoolean(false);
            cursor = result.path("next_cursor").asLong(0L);
        }
        return users;
    }

    public UserDetail getUserDetail(String accessToken, String userId) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("userid", userId);
        body.put("language", "zh_CN");
        JsonNode response = postTopApi(GET_USER_URL, accessToken, body);
        JsonNode result = response.path("result");
        return new UserDetail(
            textOrNull(result, "userid"),
            textOrNull(result, "unionid"),
            textOrNull(result, "name"),
            textOrNull(result, "mobile"),
            textOrNull(result, "email")
        );
    }

    public String resolveAccessToken(IdpBindMode bindMode, String idpApplicationCode, String corpId) {
        DingTalkIamProperties.Credential credential = resolveCredentialForContactSync(bindMode, idpApplicationCode, corpId);
        return resolveAccessToken(credential, bindMode);
    }

    /**
     * 校验通讯录同步上下文与 IAM 凭证一致，并返回对应凭证。
     */
    public DingTalkIamProperties.Credential resolveCredentialForContactSync(
        IdpBindMode bindMode,
        String idpApplicationCode,
        String corpId
    ) {
        DingTalkIamProperties.Credential credential = credential(bindMode);
        requireCredential(credential);
        validateApplicationCode(credential, idpApplicationCode);
        validateCorpId(bindMode, credential, corpId);
        return credential;
    }

    static UserSummary parseUserSummary(JsonNode item) {
        List<Long> deptIds = new ArrayList<>();
        JsonNode deptIdList = item.path("dept_id_list");
        if (deptIdList.isArray()) {
            deptIdList.forEach(node -> deptIds.add(node.asLong()));
        }
        return new UserSummary(
            textOrNull(item, "userid"),
            textOrNull(item, "unionid"),
            textOrNull(item, "name"),
            textOrNull(item, "mobile"),
            textOrNull(item, "email"),
            deptIds
        );
    }

    private String resolveAccessToken(DingTalkIamProperties.Credential credential, IdpBindMode bindMode) {
        String cacheKey = bindMode.name() + ":" + credential.getClientId();
        CachedToken cached = tokenCache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            return cached.accessToken();
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("appKey", credential.getClientId());
        body.put("appSecret", credential.getClientSecret());
        try {
            String responseBody = restClient.post()
                .uri(ACCESS_TOKEN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .body(String.class);
            JsonNode json = objectMapper.readTree(responseBody);
            String accessToken = textOrNull(json, "accessToken");
            if (Strings.isBlank(accessToken)) {
                throw new BusinessException(IamErrorCode.DINGTALK_CONTACT_ACCESS_TOKEN_FAILED);
            }
            long expireIn = json.path("expireIn").asLong(7200L);
            tokenCache.put(cacheKey, new CachedToken(accessToken, System.currentTimeMillis() + (expireIn - 120) * 1000));
            return accessToken;
        } catch (RestClientException | java.io.IOException e) {
            log.error("dingtalk access token failed bindMode={}", bindMode, e);
            throw new BusinessException(IamErrorCode.DINGTALK_CONTACT_ACCESS_TOKEN_FAILED);
        }
    }

    private void collectDepartments(String accessToken, long parentDeptId, List<DepartmentInfo> result) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("dept_id", parentDeptId);
        JsonNode response = postTopApi(LIST_SUB_DEPT_URL, accessToken, body);
        JsonNode list = response.path("result");
        if (!list.isArray()) {
            return;
        }
        for (JsonNode item : list) {
            long deptId = item.path("dept_id").asLong();
            long parentId = item.path("parent_id").asLong(parentDeptId);
            String name = textOrNull(item, "name");
            int order = item.path("order").asInt(0);
            result.add(new DepartmentInfo(deptId, parentId, name, order));
            collectDepartments(accessToken, deptId, result);
        }
    }

    private JsonNode postTopApi(String url, String accessToken, ObjectNode body) {
        DingTalkIamProperties.ContactSync sync = contactSync();
        int maxAttempts = Math.max(0, sync.getMaxRetries()) + 1;
        RestClientException lastTransportError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            rateLimiter.acquire();
            apiCallCount++;
            try {
                String responseBody = restClient.post()
                    .uri(url + "?access_token=" + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
                JsonNode json = objectMapper.readTree(responseBody);
                int errCode = json.path("errcode").asInt(0);
                if (errCode == 0) {
                    return json;
                }
                if (attempt < maxAttempts && DingTalkTopApiSupport.isRetryableErrCode(errCode)) {
                    retryCount++;
                    sleepBackoff(sync.getRetryBackoffMs(), attempt);
                    continue;
                }
                log.error("dingtalk topapi failed url={} errcode={} errmsg={}", url, errCode, json.path("errmsg").asText());
                throw new BusinessException(IamErrorCode.DINGTALK_CONTACT_FETCH_FAILED);
            } catch (RestClientResponseException e) {
                if (attempt < maxAttempts) {
                    retryCount++;
                    sleepBackoff(sync.getRetryBackoffMs(), attempt);
                    continue;
                }
                log.error("dingtalk topapi http failed url={} status={}", url, e.getStatusCode().value(), e);
                throw new BusinessException(IamErrorCode.DINGTALK_CONTACT_FETCH_FAILED);
            } catch (RestClientException e) {
                lastTransportError = e;
                if (attempt < maxAttempts) {
                    retryCount++;
                    sleepBackoff(sync.getRetryBackoffMs(), attempt);
                    continue;
                }
            } catch (java.io.IOException e) {
                if (attempt < maxAttempts) {
                    retryCount++;
                    sleepBackoff(sync.getRetryBackoffMs(), attempt);
                    continue;
                }
                log.error("dingtalk topapi failed url={}", url, e);
                throw new BusinessException(IamErrorCode.DINGTALK_CONTACT_FETCH_FAILED);
            }
        }
        log.error("dingtalk topapi failed url={}", url, lastTransportError);
        throw new BusinessException(IamErrorCode.DINGTALK_CONTACT_FETCH_FAILED);
    }

    private DingTalkIamProperties.ContactSync contactSync() {
        return dingTalkIamProperties.getContactSync();
    }

    private static void sleepBackoff(long baseMs, int attempt) {
        try {
            Thread.sleep(DingTalkTopApiSupport.computeRetryBackoffMs(baseMs, attempt));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private DingTalkIamProperties.Credential credential(IdpBindMode bindMode) {
        return bindMode == IdpBindMode.THIRD_PARTY
            ? dingTalkIamProperties.getThirdParty()
            : dingTalkIamProperties.getInternal();
    }

    private static void requireCredential(DingTalkIamProperties.Credential credential) {
        if (Strings.isBlank(credential.getClientId()) || Strings.isBlank(credential.getClientSecret())) {
            throw new BusinessException(IamErrorCode.DINGTALK_CONTACT_CREDENTIAL_MISSING);
        }
    }

    private static void validateApplicationCode(DingTalkIamProperties.Credential credential, String idpApplicationCode) {
        if (Strings.isBlank(idpApplicationCode)) {
            throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "idpApplicationCode");
        }
        String configuredClientId = credential.getClientId() == null ? "" : credential.getClientId().trim();
        if (!configuredClientId.equals(idpApplicationCode.trim())) {
            throw new BusinessException(IamErrorCode.DINGTALK_CONTACT_APPLICATION_MISMATCH);
        }
    }

    private static void validateCorpId(IdpBindMode bindMode, DingTalkIamProperties.Credential credential, String corpId) {
        if (Strings.isBlank(corpId)) {
            throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "corpId");
        }
        if (bindMode != IdpBindMode.INTERNAL) {
            return;
        }
        String configuredCorpId = credential.getCorpId() == null ? "" : credential.getCorpId().trim();
        if (Strings.isBlank(configuredCorpId)) {
            throw new BusinessException(IamErrorCode.DINGTALK_CONTACT_CREDENTIAL_MISSING);
        }
        if (!configuredCorpId.equals(corpId.trim())) {
            throw new BusinessException(IamErrorCode.DINGTALK_CONTACT_CORP_MISMATCH);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return Strings.isBlank(text) ? null : text;
    }

    private record CachedToken(String accessToken, long expireAtMs) {
        boolean isValid() {
            return Strings.isNotBlank(accessToken) && System.currentTimeMillis() < expireAtMs;
        }
    }
}
