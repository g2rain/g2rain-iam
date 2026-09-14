package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.dingtalk.DingTalkAdminAsserter;
import com.g2rain.iam.dingtalk.DingTalkLoginAdapter;
import com.g2rain.iam.dingtalk.DingTalkLoginAdapterRouter;
import com.g2rain.iam.dingtalk.DingTalkOAuthResult;
import com.g2rain.iam.dingtalk.DingTalkPrincipal;
import com.g2rain.iam.dto.DingTalkOAuthStateDto;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.IdpLoginRole;
import com.g2rain.iam.enums.RedisKeyRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 钉钉 OAuth 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DingTalkOAuthService {

    private final DingTalkOAuthStateService dingTalkOAuthStateService;
    private final GenericRedisHelper genericRedisHelper;
    private final DingTalkLoginAdapterRouter dingTalkLoginAdapterRouter;
    private final AuthService authService;
    private final DingTalkAdminAsserter dingTalkAdminAsserter;

    public String buildDingTalkAuthorizeRedirectUrl(String bindMode, String clientId, String redirectUri,
                                                    String state) {
        return buildDingTalkAuthorizeRedirectUrl(bindMode, clientId, redirectUri, state, null);
    }

    public String buildDingTalkAuthorizeRedirectUrl(String bindMode, String clientId, String redirectUri,
                                                    String state, String loginRole) {
        return dingTalkOAuthStateService.persistStateAndBuildAuthorizeUrl(
            bindMode, clientId, redirectUri, state, false, loginRole);
    }

    public Optional<DingTalkOAuthStateDto> peekOAuthState(String opaqueState) {
        if (Strings.isBlank(opaqueState)) {
            return Optional.empty();
        }
        return Optional.ofNullable(genericRedisHelper.get(
            RedisKeyRule.DINGTALK_OAUTH_STATE.format(opaqueState.trim()),
            DingTalkOAuthStateDto.class
        ));
    }

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

        IdpLoginRole loginRole = IdpLoginRole.fromParam(payload.getLoginRole());
        boolean snsQrLogin = Boolean.TRUE.equals(payload.getQrEmbedded());
        DingTalkLoginAdapter adapter = dingTalkLoginAdapterRouter.resolve(payload.getBindMode());
        DingTalkPrincipal principal = adapter.exchangeCodeForPrincipal(authCode, snsQrLogin);

        boolean idpAdmin = false;
        if (loginRole.isAdmin()) {
            dingTalkAdminAsserter.assertCorpAdmin(
                principal.bindMode(),
                principal.corpId(),
                principal.unionId(),
                principal.idpApplicationCode()
            );
            idpAdmin = true;
        }

        String sessionId = authService.authenticateIdpEmployee(
            principal.toIdpPrincipal(), loginRole, idpAdmin, true);
        return new DingTalkOAuthResult(
            sessionId,
            payload.getClientId(),
            payload.getRedirectUri(),
            payload.getState()
        );
    }
}
