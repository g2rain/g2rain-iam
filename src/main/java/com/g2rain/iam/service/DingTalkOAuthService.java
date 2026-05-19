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
 * 钉钉 OAuth：浏览器跳转前的授权 URL 委托 {@link DingTalkOAuthStateService}；回调换票、建会话与 {@code redirectConsent}。
 * 内嵌扫码入口见 {@link DingTalkQrBootstrapService}；Stream 发码见 {@link DingTalkStreamAuthorizationService}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DingTalkOAuthService {

    private final DingTalkOAuthStateService dingTalkOAuthStateService;
    private final GenericRedisHelper genericRedisHelper;
    private final DingTalkLoginAdapterRouter dingTalkLoginAdapterRouter;
    private final AuthService authService;

    public String buildDingTalkAuthorizeRedirectUrl(String bindMode, String clientId, String clientRedirectUri,
                                                    String clientState) {
        return dingTalkOAuthStateService.persistStateAndBuildAuthorizeUrl(
            bindMode, clientId, clientRedirectUri, clientState, false);
    }

    /**
     * 按钉钉回调中的 opaque {@code state} 读取 OAuth 上下文（不删除 Redis），供失败页恢复 {@code clientId}/{@code redirectUri}。
     */
    public Optional<DingTalkOAuthStateDto> peekOAuthState(String dingTalkState) {
        if (Strings.isBlank(dingTalkState)) {
            return Optional.empty();
        }
        return Optional.ofNullable(genericRedisHelper.get(
            RedisKeyRule.DINGTALK_OAUTH_STATE.format(dingTalkState.trim()),
            DingTalkOAuthStateDto.class
        ));
    }

    public DingTalkOAuthResult finishLogin(String authCode, String dingTalkState) {
        String key = RedisKeyRule.DINGTALK_OAUTH_STATE.format(dingTalkState);
        DingTalkOAuthStateDto payload = genericRedisHelper.get(key, DingTalkOAuthStateDto.class);
        if (payload == null) {
            log.warn("[dingtalk-oauth] invalid or expired state");
            throw new BusinessException(IamErrorCode.DINGTALK_OAUTH_INVALID_STATE);
        }
        if (Strings.isBlank(payload.getClientId()) || Strings.isBlank(payload.getClientRedirectUri())) {
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
            payload.getClientRedirectUri(),
            payload.getClientState()
        );
    }
}
