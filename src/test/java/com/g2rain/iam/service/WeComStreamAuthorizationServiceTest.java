package com.g2rain.iam.service;

import com.g2rain.basis.vo.UserVo;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.dto.WeComStreamAuthorizationDto;
import com.g2rain.iam.idp.IdpPrincipal;
import com.g2rain.iam.vo.WeComStreamAuthorizationVo;
import com.g2rain.iam.wecom.WeComLoginAdapter;
import com.g2rain.iam.wecom.WeComLoginAdapterRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeComStreamAuthorizationServiceTest {

    private WeComIamProperties weComIamProperties;
    private WeComLoginAdapterRouter weComLoginAdapterRouter;
    private AuthService authService;
    private SessionService sessionService;
    private AuthorizationService authorizationService;
    private UserService userService;
    private WeComStreamAuthorizationService service;

    @BeforeEach
    void setUp() {
        weComIamProperties = new WeComIamProperties();
        weComIamProperties.getInternal().setAgentId("1000001");
        weComLoginAdapterRouter = mock(WeComLoginAdapterRouter.class);
        authService = mock(AuthService.class);
        sessionService = mock(SessionService.class);
        authorizationService = mock(AuthorizationService.class);
        userService = mock(UserService.class);
        service = new WeComStreamAuthorizationService(
            weComIamProperties,
            weComLoginAdapterRouter,
            authService,
            sessionService,
            authorizationService,
            userService
        );
        when(weComLoginAdapterRouter.resolve("INTERNAL")).thenReturn(mock(WeComLoginAdapter.class));
    }

    @Test
    void issuesAuthorizationCodeForBoundUser() {
        WeComStreamAuthorizationDto dto = new WeComStreamAuthorizationDto();
        dto.setClientId("oauth-client");
        dto.setBindMode("INTERNAL");
        dto.setCorpId("corp-a");
        dto.setUserId("user-a");
        dto.setState("biz-state");

        SessionDto session = new SessionDto();
        session.setSessionId("session-1");
        when(authService.authenticateIdp(any(IdpPrincipal.class), eq(false))).thenReturn("session-1");
        when(sessionService.getSession("session-1")).thenReturn(session);

        UserVo user = new UserVo();
        user.setId(7L);
        when(userService.listUserVos(session)).thenReturn(List.of(user));
        when(authorizationService.generateAuthorizationCode(session, "oauth-client", "7", true))
            .thenReturn("auth-code-xyz");

        WeComStreamAuthorizationVo result = service.issueStreamAuthorizationCode(dto);

        assertEquals("auth-code-xyz", result.code());
        assertEquals("biz-state", result.state());
        verify(authService).authenticateIdp(any(IdpPrincipal.class), eq(false));
    }

    @Test
    void picksLatestUserWhenMultipleUsersExist() {
        WeComStreamAuthorizationDto dto = new WeComStreamAuthorizationDto();
        dto.setClientId("oauth-client");
        dto.setBindMode("INTERNAL");
        dto.setCorpId("corp-a");
        dto.setUserId("user-a");

        SessionDto session = new SessionDto();
        when(authService.authenticateIdp(any(IdpPrincipal.class), eq(false))).thenReturn("session-1");
        when(sessionService.getSession("session-1")).thenReturn(session);

        UserVo older = new UserVo();
        older.setId(1L);
        older.setUpdateTime("2024-01-01T00:00:00Z");
        UserVo newer = new UserVo();
        newer.setId(2L);
        newer.setUpdateTime("2025-01-01T00:00:00Z");
        when(userService.listUserVos(session)).thenReturn(List.of(older, newer));
        when(authorizationService.generateAuthorizationCode(session, "oauth-client", "2", true))
            .thenReturn("code");

        service.issueStreamAuthorizationCode(dto);

        verify(authorizationService).generateAuthorizationCode(session, "oauth-client", "2", true);
    }
}
