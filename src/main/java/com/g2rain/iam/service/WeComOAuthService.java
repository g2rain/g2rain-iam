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
import com.g2rain.iam.wecom.WeComLoginAdapterRouter;
import com.g2rain.iam.wecom.WeComOAuthResult;
import com.g2rain.iam.wecom.WeComPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WeComOAuthService {
    private final WeComOAuthStateService stateService;
    private final WeComLoginAdapterRouter weComLoginAdapterRouter;
    private final IdpEnterpriseApplicationAuthorizationClient authorizationClient;
    private final AuthService authService;

    public String buildAuthorizeUrl(
        String bindMode, String clientId, String redirectUri, String state) {
        return stateService.persistAndBuildAuthorizeUrl(
            bindMode, clientId, redirectUri, state);
    }

    public WeComOAuthResult finishLogin(String authCode, String opaqueState) {
        WeComOAuthStateDto state = stateService.consume(opaqueState);
        IdpBindMode bindMode = IdpBindMode.valueOf(state.getBindMode());
        WeComPrincipal principal = weComLoginAdapterRouter.resolve(bindMode.name())
            .exchangeCodeForPrincipal(authCode);
        if (bindMode == IdpBindMode.THIRD_PARTY) {
            validateThirdPartyAuthorization(principal);
        }
        String sessionId =
            authService.authenticateIdp(principal.toIdpPrincipal(), true);
        return new WeComOAuthResult(
            sessionId,
            state.getClientId(),
            state.getRedirectUri(),
            state.getState()
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
