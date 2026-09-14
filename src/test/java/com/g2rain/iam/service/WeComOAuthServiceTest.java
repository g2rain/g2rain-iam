package com.g2rain.iam.service;

import com.g2rain.basis.dto.IdpEnterpriseApplicationAuthorizationResolveRequest;
import com.g2rain.basis.vo.IdpEnterpriseApplicationAuthorizationResolveVo;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.model.Result;
import com.g2rain.iam.client.IdpEnterpriseApplicationAuthorizationClient;
import com.g2rain.iam.dto.WeComOAuthStateDto;
import com.g2rain.iam.wecom.ThirdPartyWeComLoginAdapter;
import com.g2rain.iam.wecom.WeComInternalAdminAsserter;
import com.g2rain.iam.wecom.WeComLoginAdapterRouter;
import com.g2rain.iam.wecom.WeComPrincipal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeComOAuthServiceTest {

    @Test
    void thirdPartyLoginRejectsAgentMismatch() {
        WeComOAuthStateService stateService = mock(WeComOAuthStateService.class);
        WeComLoginAdapterRouter adapterRouter = mock(WeComLoginAdapterRouter.class);
        ThirdPartyWeComLoginAdapter adapter = mock(ThirdPartyWeComLoginAdapter.class);
        IdpEnterpriseApplicationAuthorizationClient authorizationClient =
            mock(IdpEnterpriseApplicationAuthorizationClient.class);
        AuthService authService = mock(AuthService.class);
        WeComInternalAdminAsserter adminAsserter = mock(WeComInternalAdminAsserter.class);
        WeComOAuthService service = new WeComOAuthService(
            stateService, adapterRouter, authorizationClient, authService, adminAsserter);

        WeComOAuthStateDto state = new WeComOAuthStateDto();
        state.setBindMode("THIRD_PARTY");
        state.setClientId("client");
        state.setRedirectUri("https://example.test/callback");
        state.setLoginRole("USER");
        when(stateService.consume("state")).thenReturn(state);
        when(adapterRouter.resolve("THIRD_PARTY")).thenReturn(adapter);
        when(adapter.exchangeCodeForPrincipal(eq("code"), eq("member")))
            .thenReturn(new WeComPrincipal(
                "corp", "user", "open", "name", "THIRD_PARTY",
                "suite", "agent-from-login", "{}"));
        IdpEnterpriseApplicationAuthorizationResolveVo authorization =
            new IdpEnterpriseApplicationAuthorizationResolveVo();
        authorization.setAuthorizationStatus("ACTIVE");
        authorization.setInstalledApplicationId("other-agent");
        when(authorizationClient.resolve(
            any(IdpEnterpriseApplicationAuthorizationResolveRequest.class)))
            .thenReturn(Result.success(authorization));

        assertThrows(BusinessException.class,
            () -> service.finishLogin("code", "state"));
    }
}
