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
 * 钉钉 OAuth 服务：构建授权跳转 URL，并在回调中完成换票与会话建立。
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

    /**
     * 构建钉钉授权页重定向 URL（无 loginRole / applicationCode / tid）。
     */
    public String buildDingTalkAuthorizeRedirectUrl(String bindMode, String clientId, String redirectUri,
                                                    String state) {
        return buildDingTalkAuthorizeRedirectUrl(bindMode, clientId, redirectUri, state, null, null);
    }

    /**
     * 构建钉钉授权页重定向 URL（可指定 loginRole）。
     */
    public String buildDingTalkAuthorizeRedirectUrl(String bindMode, String clientId, String redirectUri,
                                                    String state, String loginRole) {
        return buildDingTalkAuthorizeRedirectUrl(bindMode, clientId, redirectUri, state, loginRole, null);
    }

    /**
     * 构建钉钉授权页重定向 URL（可指定 loginRole 与 applicationCode）。
     */
    public String buildDingTalkAuthorizeRedirectUrl(String bindMode, String clientId, String redirectUri,
                                                    String state, String loginRole, String applicationCode) {
        return buildDingTalkAuthorizeRedirectUrl(
            bindMode, clientId, redirectUri, state, loginRole, applicationCode, null);
    }

    /**
     * 构建钉钉授权页重定向 URL，并将授权事务 {@code transactionId} 写入 OAuth state。
     *
     * @param bindMode        IdP 接入形态
     * @param clientId        OAuth 客户端 ID
     * @param redirectUri     OAuth 回调地址
     * @param state           业务 state
     * @param loginRole       登录角色（可选）
     * @param applicationCode 目标应用编码（可选）
     * @param transactionId   授权事务 tid（可选）
     * @return 钉钉授权页完整 URL
     */
    public String buildDingTalkAuthorizeRedirectUrl(String bindMode, String clientId, String redirectUri,
                                                    String state, String loginRole, String applicationCode,
                                                    String transactionId) {
        return dingTalkOAuthStateService.persistStateAndBuildAuthorizeUrl(
            bindMode, clientId, redirectUri, state, false, loginRole, applicationCode, transactionId);
    }

    /**
     * 只读查看 OAuth state（不消费），用于回调失败时回显登录页。
     *
     * @param opaqueState 不透明 state
     * @return state 载荷；不存在则为 empty
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
     * 钉钉授权回调：消费 state、换票、校验管理员角色并建立 IAM 会话。
     *
     * @param authCode    钉钉授权码
     * @param opaqueState 不透明 state
     * @return 含 sessionId 与 transactionId 的登录结果
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
            payload.getState(),
            payload.getApplicationCode(),
            payload.getTransactionId()
        );
    }
}
