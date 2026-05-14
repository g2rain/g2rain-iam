package com.g2rain.iam.service;

import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.basis.vo.UserVo;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.config.DingTalkIamProperties;
import com.g2rain.iam.dto.DingTalkOAuthStateDto;
import com.g2rain.iam.dto.DingTalkStreamAuthorizationDto;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.dingtalk.DingTalkLoginAdapter;
import com.g2rain.iam.dingtalk.DingTalkLoginAdapterRouter;
import com.g2rain.iam.dingtalk.DingTalkOAuthResult;
import com.g2rain.iam.dingtalk.DingTalkPrincipal;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.RedisKeyRule;
import com.g2rain.iam.utils.IamUtils;
import com.g2rain.iam.vo.DingTalkQrBootstrapVo;
import com.g2rain.iam.vo.DingTalkStreamAuthorizationVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * 钉钉 OAuth：Redis 托管 state、浏览器换票与 {@code redirectConsent}；以及 Stream/消息应用侧基于 unionId 的发码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DingTalkOAuthService {

    private final DingTalkIamProperties dingTalkIamProperties;
    private final GenericRedisHelper genericRedisHelper;
    private final DingTalkLoginAdapterRouter dingTalkLoginAdapterRouter;
    private final AuthService authService;
    private final AuthorizationService authorizationService;
    private final UserService userService;

    /**
     * 内嵌扫码：生成与 {@link #buildDingTalkAuthorizeRedirectUrl} 相同的钉钉 {@code goto} URL（Redis state 已写入）。
     *
     * @param clientId            OAuth 客户端 ID（与跳转授权一致）
     * @param clientRedirectUri   OAuth redirect_uri
     * @param clientState         OAuth state（可选）
     */
    public DingTalkQrBootstrapVo buildQrBootstrap(String bindMode, String clientId, String clientRedirectUri,
                                                  String clientState) {
        String gotoUrl = buildDingTalkAuthorizeRedirectUrl(bindMode, clientId, clientRedirectUri, clientState);
        return new DingTalkQrBootstrapVo(gotoUrl);
    }

    /**
     * 生成 opaque state 写入 Redis，返回钉钉授权页完整 URL（跳转授权与内嵌扫码共用）。
     *
     * @param clientId            OAuth 客户端 ID（与后续 {@code /auth/authorize} 一致）
     * @param clientRedirectUri   OAuth redirect_uri（与后续 {@code /auth/authorize} 一致）
     * @param clientState         OAuth state（可选）
     */
    public String buildDingTalkAuthorizeRedirectUrl(String bindMode, String clientId, String clientRedirectUri,
                                                    String clientState) {
        if (Strings.isBlank(clientId)) {
            throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "clientId");
        }
        if (Strings.isBlank(clientRedirectUri)) {
            throw new BusinessException(SystemErrorCode.PARAM_REQUIRED, "redirectUri");
        }
        DingTalkLoginAdapter adapter = dingTalkLoginAdapterRouter.resolve(bindMode);
        String opaque = IamUtils.generateAuthorizationCode();
        DingTalkOAuthStateDto payload = new DingTalkOAuthStateDto();
        payload.setBindMode(bindMode);
        payload.setClientId(clientId);
        payload.setClientRedirectUri(clientRedirectUri);
        payload.setClientState(clientState);
        genericRedisHelper.set(
            RedisKeyRule.DINGTALK_OAUTH_STATE.format(opaque),
            payload,
            Duration.ofMinutes(10)
        );
        return adapter.buildAuthorizeUrl(opaque, dingTalkIamProperties.fullCallbackUrl());
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

    /**
     * Stream / 消息应用侧：须已存在 Basis 绑定（不自动注册）；建会话、发放 OAuth 授权码。
     */
    public DingTalkStreamAuthorizationVo issueStreamAuthorizationCode(DingTalkStreamAuthorizationDto req) {
        dingTalkLoginAdapterRouter.resolve(req.getBindMode());

        String unionId = req.getUnionId().trim();
        String idpAppCode = oauthClientIdForBindMode(req.getBindMode());
        if (Strings.isBlank(idpAppCode)) {
            throw new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "dingtalk clientId");
        }

        DingTalkPrincipal principal = new DingTalkPrincipal(
            unionId,
            null,
            Strings.isBlank(req.getCorpId()) ? null : req.getCorpId().trim(),
            "",
            req.getBindMode(),
            "{}",
            idpAppCode
        );
        String sessionId = authService.authenticateDingTalk(principal, false);
        SessionDto session = authService.getSession(sessionId);
        if (session == null) {
            throw new BusinessException(SystemErrorCode.UNAUTHENTICATED, "session");
        }

        String userIdStr = resolveUserId(session);
        String code = authorizationService.generateAuthorizationCode(session, req.getClientId(), userIdStr, true);
        return new DingTalkStreamAuthorizationVo(code, req.getState());
    }

    private String oauthClientIdForBindMode(String bindMode) {
        if (IdpBindMode.THIRD_PARTY.name().equals(bindMode)) {
            String v = dingTalkIamProperties.getThirdParty().getClientId();
            return v == null ? "" : v.trim();
        }
        String v = dingTalkIamProperties.getInternal().getClientId();
        return v == null ? "" : v.trim();
    }

    /**
     * 无用户时为 null；单用户取该用户；多用户时取 {@link UserVo#getUpdateTime()} 最新的一条
     * （时间为空或相同时取更大 {@link UserVo#getId()}）。
     */
    private String resolveUserId(SessionDto session) {
        List<UserVo> users = userService.listUserVos(session);
        if (Collections.isEmpty(users)) {
            return null;
        }
        if (users.size() == 1) {
            return String.valueOf(users.getFirst().getId());
        }
        UserVo latest = users.stream().max(
            Comparator.<UserVo, String>comparing(u -> Strings.isBlank(u.getUpdateTime()) ? "" : u.getUpdateTime())
                .thenComparing(u -> u.getId() != null ? u.getId() : 0L)
        ).orElse(users.getFirst());
        return String.valueOf(latest.getId());
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
