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

import java.net.URI;

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

    /**
     * 生成 opaque state 写入 Redis，返回钉钉授权页完整 URL（浏览器跳转授权）。
     *
     * @param clientId            OAuth 客户端 ID（与后续 {@code /auth/authorize} 一致）
     * @param clientRedirectUri   OAuth redirect_uri（与后续 {@code /auth/authorize} 一致）
     * @param clientState         OAuth state（可选）
     */
    public String buildDingTalkAuthorizeRedirectUrl(String bindMode, String clientId, String clientRedirectUri,
                                                    String clientState) {
        return dingTalkOAuthStateService.persistStateAndBuildAuthorizeUrl(
            bindMode, clientId, clientRedirectUri, clientState, false);
    }

    /**
     * 校验 state、换票、建立 IAM 会话；返回会话与 OAuth 参数，由控制器调用 {@link com.g2rain.iam.service.ModelAndViewService#redirectConsent}。
     */
    public DingTalkOAuthResult finishLogin(String authCode, String dingTalkState) {
        log.info(
            "[dingtalk-oauth] finishLogin enter authCodeLen={} stateLen={}",
            authCode == null ? 0 : authCode.length(),
            dingTalkState == null ? 0 : dingTalkState.length()
        );
        String key = RedisKeyRule.DINGTALK_OAUTH_STATE.format(dingTalkState);
        DingTalkOAuthStateDto payload = genericRedisHelper.get(key, DingTalkOAuthStateDto.class);
        if (payload == null) {
            log.warn("[dingtalk-oauth] finishLogin state not found or expired in Redis keySuffixLen={}",
                dingTalkState == null ? 0 : dingTalkState.length());
            throw new BusinessException(IamErrorCode.DINGTALK_OAUTH_INVALID_STATE);
        }
        if (Strings.isBlank(payload.getClientId()) || Strings.isBlank(payload.getClientRedirectUri())) {
            log.warn("[dingtalk-oauth] finishLogin payload missing oauth clientId or redirectUri bindMode={}",
                payload.getBindMode());
            throw new BusinessException(IamErrorCode.DINGTALK_OAUTH_INVALID_STATE);
        }
        genericRedisHelper.delete(key);

        log.info(
            "[dingtalk-oauth] finishLogin after state consume bindMode={} oauthClientIdLen={} redirectUriHost={}",
            payload.getBindMode(),
            payload.getClientId() == null ? 0 : payload.getClientId().length(),
            summarizeRedirectHost(payload.getClientRedirectUri())
        );

        DingTalkLoginAdapter adapter = dingTalkLoginAdapterRouter.resolve(payload.getBindMode());
        DingTalkPrincipal principal = adapter.exchangeCodeForPrincipal(authCode);
        String sessionId = authService.authenticateDingTalk(principal, true);
        log.info(
            "[dingtalk-oauth] finishLogin session established bindMode={} sessionIdLen={}",
            payload.getBindMode(),
            sessionId == null ? 0 : sessionId.length()
        );
        return new DingTalkOAuthResult(
            sessionId,
            payload.getClientId(),
            payload.getClientRedirectUri(),
            payload.getClientState()
        );
    }

    /** 仅打 redirect_uri 的 host，避免整 URL 进日志。 */
    private static String summarizeRedirectHost(String redirectUri) {
        if (Strings.isBlank(redirectUri)) {
            return "(blank)";
        }
        try {
            String host = URI.create(redirectUri.trim()).getHost();
            return host != null ? host : "(no-host)";
        } catch (IllegalArgumentException ignored) {
            return "(invalid-uri)";
        }
    }
}
