package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.dto.DingTalkOAuthStateDto;
import com.g2rain.iam.dingtalk.DingTalkLoginAdapter;
import com.g2rain.iam.dingtalk.DingTalkLoginAdapterRouter;
import com.g2rain.iam.dingtalk.DingTalkOAuthResult;
import com.g2rain.iam.dingtalk.DingTalkPrincipal;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.RedisKeyRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 钉钉 OAuth 服务
 * 功能：浏览器授权跳转、回调换票建会话；内嵌扫码与 Stream 发码委托 {@link DingTalkQrBootstrapService}、{@link DingTalkStreamAuthorizationService}
 *
 * @author Alpha
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DingTalkOAuthService {

    private final DingTalkOAuthStateService dingTalkOAuthStateService;
    private final GenericRedisHelper genericRedisHelper;
    private final DingTalkLoginAdapterRouter dingTalkLoginAdapterRouter;
    private final AuthService authService;

    /**
     * 生成钉钉浏览器授权跳转 URL（方式一）
     *
     * @param bindMode    IdP 接入形态
     * @param clientId    OAuth2 客户端 ID
     * @param redirectUri OAuth2 回调地址
     * @param state       业务系统 state
     * @return 钉钉授权页完整 URL
     */
    public String buildDingTalkAuthorizeRedirectUrl(String bindMode, String clientId, String redirectUri,
                                                    String state) {
        return dingTalkOAuthStateService.persistStateAndBuildAuthorizeUrl(
            bindMode, clientId, redirectUri, state, false);
    }

    /**
     * 按 opaque state 读取 OAuth 上下文（不删除 Redis）
     *
     * @param opaqueState 钉钉回调中的 opaque state
     * @return OAuth state 载荷，不存在或为空时返回空 Optional
     */
    public Optional<DingTalkOAuthStateDto> peekOAuthState(String opaqueState) {
        if (Strings.isBlank(opaqueState)) {
            return Optional.empty();
        }
        return Optional.ofNullable(genericRedisHelper.get(
            RedisKeyRule.DINGTALK_OAUTH_STATE.format(opaqueState.trim()),
            DingTalkOAuthStateDto.class
        ));
    }

    /**
     * 使用授权码完成换票、建会话，并返回后续 OAuth 重定向所需参数
     *
     * @param authCode    钉钉授权码
     * @param opaqueState IAM opaque state
     * @return 会话 ID 与 OAuth 客户端参数
     */
    public DingTalkOAuthResult finishLogin(String authCode, String opaqueState) {
        String key = RedisKeyRule.DINGTALK_OAUTH_STATE.format(opaqueState);
        DingTalkOAuthStateDto payload = genericRedisHelper.get(key, DingTalkOAuthStateDto.class);
        if (payload == null) {
            log.warn("[dingtalk-oauth] invalid or expired state");
            throw new BusinessException(IamErrorCode.DINGTALK_OAUTH_INVALID_STATE);
        }
        if (Strings.isBlank(payload.getClientId()) || Strings.isBlank(payload.getRedirectUri())) {
            log.warn("[dingtalk-oauth] state payload missing oauth clientId or redirectUri bindMode={}",
                payload.getBindMode());
            throw new BusinessException(IamErrorCode.DINGTALK_OAUTH_INVALID_STATE);
        }
        genericRedisHelper.delete(key);

        boolean snsQrLogin = Boolean.TRUE.equals(payload.getQrEmbedded());
        DingTalkLoginAdapter adapter = dingTalkLoginAdapterRouter.resolve(payload.getBindMode());
        DingTalkPrincipal principal = adapter.exchangeCodeForPrincipal(authCode, snsQrLogin);
        String sessionId = authService.authenticateDingTalk(principal, true);
        return new DingTalkOAuthResult(
            sessionId,
            payload.getClientId(),
            payload.getRedirectUri(),
            payload.getState()
        );
    }
}
