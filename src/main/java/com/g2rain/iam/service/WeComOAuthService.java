package com.g2rain.iam.service;

import com.g2rain.basis.dto.IdpEnterpriseApplicationAuthorizationResolveRequest;
import com.g2rain.basis.enums.IdpApplicationAuthorizationStatus;
import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.basis.enums.IdpType;
import com.g2rain.basis.vo.IdpEnterpriseApplicationAuthorizationResolveVo;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.client.IdpEnterpriseApplicationAuthorizationClient;
import com.g2rain.iam.dto.WeComOAuthStateDto;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.IdpLoginRole;
import com.g2rain.iam.wecom.ThirdPartyWeComLoginAdapter;
import com.g2rain.iam.wecom.WeComInternalAdminAsserter;
import com.g2rain.iam.wecom.WeComLoginAdapter;
import com.g2rain.iam.wecom.WeComLoginAdapterRouter;
import com.g2rain.iam.wecom.WeComOAuthResult;
import com.g2rain.iam.wecom.WeComPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 企业微信 OAuth 服务：构建扫码授权 URL，并在回调中完成换票与会话建立。
 */
@Service
@RequiredArgsConstructor
public class WeComOAuthService {
    private final WeComOAuthStateService stateService;
    private final WeComLoginAdapterRouter weComLoginAdapterRouter;
    private final IdpEnterpriseApplicationAuthorizationClient authorizationClient;
    private final AuthService authService;
    private final WeComInternalAdminAsserter weComInternalAdminAsserter;

    /**
     * 构建企业微信扫码授权 URL（无 loginRole / applicationCode / tid）。
     */
    public String buildAuthorizeUrl(
        String bindMode, String clientId, String redirectUri, String state) {
        return buildAuthorizeUrl(bindMode, clientId, redirectUri, state, null, null);
    }

    /**
     * 构建企业微信扫码授权 URL（可指定 loginRole）。
     */
    public String buildAuthorizeUrl(
        String bindMode, String clientId, String redirectUri, String state, String loginRole) {
        return buildAuthorizeUrl(bindMode, clientId, redirectUri, state, loginRole, null);
    }

    /**
     * 构建企业微信扫码授权 URL（可指定 loginRole 与 applicationCode）。
     */
    public String buildAuthorizeUrl(
        String bindMode, String clientId, String redirectUri, String state, String loginRole,
        String applicationCode) {
        return buildAuthorizeUrl(bindMode, clientId, redirectUri, state, loginRole, applicationCode, null);
    }

    /**
     * 构建企业微信扫码授权 URL，并将授权事务 {@code transactionId} 写入 OAuth state。
     *
     * @param bindMode        IdP 接入形态
     * @param clientId        OAuth 客户端 ID
     * @param redirectUri     OAuth 回调地址
     * @param state           业务 state
     * @param loginRole       登录角色（可选）
     * @param applicationCode 目标应用编码（可选）
     * @param transactionId   授权事务 tid（可选）
     * @return 企业微信授权页完整 URL
     */
    public String buildAuthorizeUrl(
        String bindMode, String clientId, String redirectUri, String state, String loginRole,
        String applicationCode, String transactionId) {
        return stateService.persistAndBuildAuthorizeUrl(
            bindMode, clientId, redirectUri, state, loginRole, applicationCode, transactionId);
    }

    /**
     * 企业微信授权回调：消费 state、换票、校验管理员/第三方授权并建立 IAM 会话。
     *
     * @param authCode    企业微信授权码
     * @param opaqueState 不透明 state
     * @return 含 sessionId 与 transactionId 的登录结果
     */
    public WeComOAuthResult finishLogin(String authCode, String opaqueState) {
        WeComOAuthStateDto state = stateService.consume(opaqueState);
        IdpBindMode bindMode = IdpBindMode.valueOf(state.getBindMode());
        IdpLoginRole loginRole = IdpLoginRole.fromParam(state.getLoginRole());
        WeComLoginAdapter adapter = weComLoginAdapterRouter.resolve(bindMode.name());

        WeComPrincipal principal;
        boolean idpAdmin = false;
        if (bindMode == IdpBindMode.THIRD_PARTY && adapter instanceof ThirdPartyWeComLoginAdapter thirdParty) {
            String expected = loginRole.isAdmin() ? "admin" : "member";
            principal = thirdParty.exchangeCodeForPrincipal(authCode, expected);
            idpAdmin = loginRole.isAdmin();
            validateThirdPartyAuthorization(principal);
        } else {
            principal = adapter.exchangeCodeForPrincipal(authCode);
            if (loginRole.isAdmin()) {
                weComInternalAdminAsserter.assertCorpAdmin(principal.userId());
                idpAdmin = true;
            }
        }

        String sessionId = authService.authenticateIdpEmployee(
            principal.toIdpPrincipal(), loginRole, idpAdmin, true);
        return new WeComOAuthResult(
            sessionId,
            state.getClientId(),
            state.getRedirectUri(),
            state.getState(),
            state.getApplicationCode(),
            state.getTransactionId()
        );
    }

    private void validateThirdPartyAuthorization(WeComPrincipal principal) {
        IdpEnterpriseApplicationAuthorizationResolveRequest request =
            new IdpEnterpriseApplicationAuthorizationResolveRequest();
        request.setIdpType(IdpType.WECHAT_WORK.name());
        request.setBindMode(IdpBindMode.THIRD_PARTY.name());
        request.setIdpApplicationCode(principal.idpApplicationCode());
        request.setEnterpriseId(principal.corpId());
        Result<IdpEnterpriseApplicationAuthorizationResolveVo> result =
            authorizationClient.resolve(request);
        if (!result.isSuccess()) {
            throw ExceptionConverter.of(result);
        }
        IdpEnterpriseApplicationAuthorizationResolveVo authorization =
            result.getData();
        if (authorization == null
            || !IdpApplicationAuthorizationStatus.ACTIVE.name()
            .equals(authorization.getAuthorizationStatus())) {
            throw new BusinessException(IamErrorCode.WECOM_ENTERPRISE_NOT_AUTHORIZED);
        }
        if (Strings.isBlank(principal.installedApplicationId())
            || Strings.isBlank(authorization.getInstalledApplicationId())
            || !principal.installedApplicationId().trim()
            .equals(authorization.getInstalledApplicationId().trim())) {
            throw new BusinessException(IamErrorCode.WECOM_AGENT_MISMATCH);
        }
    }
}
