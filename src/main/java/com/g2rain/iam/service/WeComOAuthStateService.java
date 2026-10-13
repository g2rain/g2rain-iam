package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Strings;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.config.IamAccessProperties;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.dto.WeComOAuthStateDto;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.IdpLoginRole;
import com.g2rain.iam.enums.RedisKeyRule;
import com.g2rain.iam.utils.IamUtils;
import com.g2rain.iam.wecom.WeComLoginAdapter;
import com.g2rain.iam.wecom.WeComLoginAdapterRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 企业微信 OAuth State 服务。
 * <p>
 * 存储：Redis，键规则见 {@link RedisKeyRule#WECOM_OAUTH_STATE}。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class WeComOAuthStateService {

    private final IamAccessProperties iamAccessProperties;
    private final WeComIamProperties properties;
    private final GenericRedisHelper redis;
    private final WeComLoginAdapterRouter weComLoginAdapterRouter;

    /**
     * 持久化 OAuth 上下文并生成企业微信授权页 URL。
     */
    public String persistAndBuildAuthorizeUrl(
        String bindMode, String clientId, String redirectUri, String state) {
        return persistAndBuildAuthorizeUrl(bindMode, clientId, redirectUri, state, null, null);
    }

    /**
     * 持久化 OAuth 上下文并生成企业微信授权页 URL（可指定 loginRole）。
     */
    public String persistAndBuildAuthorizeUrl(
        String bindMode, String clientId, String redirectUri, String state, String loginRole) {
        return persistAndBuildAuthorizeUrl(bindMode, clientId, redirectUri, state, loginRole, null);
    }

    /**
     * 持久化 OAuth 上下文并生成企业微信授权页 URL（可指定 loginRole 与 applicationCode）。
     */
    public String persistAndBuildAuthorizeUrl(
        String bindMode, String clientId, String redirectUri, String state, String loginRole,
        String applicationCode) {
        return persistAndBuildAuthorizeUrl(
            bindMode, clientId, redirectUri, state, loginRole, applicationCode, null);
    }

    /**
     * 持久化 OAuth 上下文（含授权事务 tid）并生成企业微信扫码授权 URL。
     *
     * @param bindMode        IdP 接入形态
     * @param clientId        OAuth2 客户端 ID
     * @param redirectUri     OAuth2 回调地址
     * @param state           业务系统 state
     * @param loginRole       登录角色（可选）
     * @param applicationCode 目标应用编码（可选）
     * @param transactionId   授权事务 tid（可选）
     * @return 企业微信授权页完整 URL
     */
    public String persistAndBuildAuthorizeUrl(
        String bindMode, String clientId, String redirectUri, String state, String loginRole,
        String applicationCode, String transactionId) {
        if (Strings.isBlank(clientId)) {
            throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "clientId");
        }
        if (Strings.isBlank(redirectUri)) {
            throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "redirectUri");
        }
        WeComLoginAdapter adapter = weComLoginAdapterRouter.resolve(bindMode);
        String opaqueState = IamUtils.generateAuthorizationCode();
        IdpLoginRole role = IdpLoginRole.fromParam(loginRole);
        WeComOAuthStateDto payload = new WeComOAuthStateDto();
        payload.setBindMode(adapter.bindMode().name());
        payload.setClientId(clientId.trim());
        payload.setRedirectUri(redirectUri.trim());
        payload.setState(state);
        payload.setApplicationCode(Strings.isBlank(applicationCode) ? null : applicationCode.trim());
        payload.setLoginRole(role.name());
        payload.setTransactionId(Strings.isBlank(transactionId) ? null : transactionId.trim());
        redis.set(RedisKeyRule.WECOM_OAUTH_STATE.format(opaqueState),
            payload, Duration.ofMinutes(10));
        String callback =
            properties.fullCallbackUrl(iamAccessProperties.normalizedBaseUrl());
        String weComUserType = role.isAdmin() ? "admin" : "member";
        return adapter.buildAuthorizeUrl(opaqueState, callback, weComUserType);
    }

    /**
     * 消费（读取并删除）OAuth state；无效或过期时抛业务异常。
     *
     * @param opaqueState 不透明 state
     * @return state 载荷
     */
    public WeComOAuthStateDto consume(String opaqueState) {
        if (Strings.isBlank(opaqueState)) {
            throw new BusinessException(IamErrorCode.WECOM_OAUTH_INVALID_STATE);
        }
        String key = RedisKeyRule.WECOM_OAUTH_STATE.format(opaqueState.trim());
        WeComOAuthStateDto payload = redis.get(key, WeComOAuthStateDto.class);
        if (payload == null) {
            throw new BusinessException(IamErrorCode.WECOM_OAUTH_INVALID_STATE);
        }
        redis.delete(key);
        return payload;
    }
}
