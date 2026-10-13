package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Strings;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.config.DingTalkIamProperties;
import com.g2rain.iam.config.IamAccessProperties;
import com.g2rain.iam.dto.DingTalkOAuthStateDto;
import com.g2rain.iam.dingtalk.DingTalkLoginAdapter;
import com.g2rain.iam.dingtalk.DingTalkLoginAdapterRouter;
import com.g2rain.iam.enums.RedisKeyRule;
import com.g2rain.iam.enums.IdpLoginRole;
import com.g2rain.iam.utils.IamUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 钉钉 OAuth State 服务
 * 存储: Redis，键规则见 {@link RedisKeyRule#DINGTALK_OAUTH_STATE}
 *
 * @author Alpha
 */
@Service
@RequiredArgsConstructor
public class DingTalkOAuthStateService {

    private final IamAccessProperties iamAccessProperties;
    private final DingTalkIamProperties dingTalkIamProperties;
    private final GenericRedisHelper genericRedisHelper;
    private final DingTalkLoginAdapterRouter dingTalkLoginAdapterRouter;

    /**
     * 持久化 OAuth 上下文并生成钉钉授权页 URL
     *
     * @param bindMode    IdP 接入形态
     * @param clientId    OAuth2 客户端 ID
     * @param redirectUri OAuth2 回调地址
     * @param state       业务系统 state
     * @param qrEmbedded  {@code true} 时返回方式二内嵌扫码 sns_authorize 的 goto URL
     * @return 钉钉授权页或 sns goto 完整 URL
     */
    public String persistStateAndBuildAuthorizeUrl(String bindMode, String clientId, String redirectUri,
                                                   String state, boolean qrEmbedded) {
        return persistStateAndBuildAuthorizeUrl(bindMode, clientId, redirectUri, state, qrEmbedded, null, null);
    }

    /**
     * 持久化 OAuth 上下文并生成钉钉授权页 URL（可指定 loginRole）。
     */
    public String persistStateAndBuildAuthorizeUrl(String bindMode, String clientId, String redirectUri,
                                                   String state, boolean qrEmbedded, String loginRole) {
        return persistStateAndBuildAuthorizeUrl(bindMode, clientId, redirectUri, state, qrEmbedded, loginRole, null);
    }

    /**
     * 持久化 OAuth 上下文并生成钉钉授权页 URL（可指定 loginRole 与 applicationCode）。
     */
    public String persistStateAndBuildAuthorizeUrl(String bindMode, String clientId, String redirectUri,
                                                   String state, boolean qrEmbedded, String loginRole,
                                                   String applicationCode) {
        return persistStateAndBuildAuthorizeUrl(
            bindMode, clientId, redirectUri, state, qrEmbedded, loginRole, applicationCode, null);
    }

    /**
     * 持久化 OAuth 上下文（含授权事务 tid）并生成钉钉授权页或内嵌扫码 goto URL。
     *
     * @param bindMode        IdP 接入形态
     * @param clientId        OAuth2 客户端 ID
     * @param redirectUri     OAuth2 回调地址
     * @param state           业务系统 state
     * @param qrEmbedded      {@code true} 时返回方式二内嵌扫码 sns_authorize 的 goto URL
     * @param loginRole       登录角色（可选）
     * @param applicationCode 目标应用编码（可选）
     * @param transactionId   授权事务 tid（可选）
     * @return 钉钉授权页或 sns goto 完整 URL
     */
    public String persistStateAndBuildAuthorizeUrl(String bindMode, String clientId, String redirectUri,
                                                   String state, boolean qrEmbedded, String loginRole,
                                                   String applicationCode, String transactionId) {
        if (Strings.isBlank(clientId)) {
            throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "clientId");
        }
        if (Strings.isBlank(redirectUri)) {
            throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "redirectUri");
        }
        DingTalkLoginAdapter adapter = dingTalkLoginAdapterRouter.resolve(bindMode);
        String opaqueState = IamUtils.generateAuthorizationCode();
        DingTalkOAuthStateDto payload = new DingTalkOAuthStateDto();
        payload.setBindMode(bindMode);
        payload.setClientId(clientId);
        payload.setRedirectUri(redirectUri);
        payload.setState(state);
        payload.setApplicationCode(Strings.isBlank(applicationCode) ? null : applicationCode.trim());
        payload.setTransactionId(Strings.isBlank(transactionId) ? null : transactionId.trim());
        payload.setQrEmbedded(qrEmbedded);
        payload.setLoginRole(IdpLoginRole.fromParam(loginRole).name());
        genericRedisHelper.set(
            RedisKeyRule.DINGTALK_OAUTH_STATE.format(opaqueState),
            payload,
            Duration.ofMinutes(10)
        );
        String callback = dingTalkIamProperties.fullCallbackUrl(iamAccessProperties.normalizedBaseUrl());
        if (qrEmbedded) {
            return adapter.buildQrEmbeddedAuthorizeUrl(opaqueState, callback);
        }
        return adapter.buildAuthorizeUrl(opaqueState, callback);
    }
}
