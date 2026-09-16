package com.g2rain.iam.service;

import com.g2rain.common.enums.OrganType;
import com.g2rain.common.enums.SessionType;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.model.Result;
import com.g2rain.common.web.ApplicationScope;
import com.g2rain.common.web.TokenJWTPayload;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.client.LoginTokenClient;
import com.g2rain.iam.client.MemberClient;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.member.vo.MemberVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenServiceMemberGrantTest {

    private TokenService tokenService;
    private MemberClient memberClient;
    private LoginTokenClient loginTokenClient;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(mock(GenericRedisHelper.class), mock(TokenKeyManager.class));
        memberClient = mock(MemberClient.class);
        loginTokenClient = mock(LoginTokenClient.class);
        ReflectionTestUtils.setField(tokenService, "memberClient", memberClient);
        ReflectionTestUtils.setField(tokenService, "loginTokenClient", loginTokenClient);
    }

    @Test
    void refreshMemberTokenRejectsWhenMemberNotEligible() {
        TokenJWTPayload body = memberBody();
        when(memberClient.requireActiveForToken(10001L, 9L))
            .thenReturn(Result.error(IamErrorCode.MEMBER_TOKEN_ISSUE_DENIED));

        BusinessException ex = assertThrows(BusinessException.class, () ->
            ReflectionTestUtils.invokeMethod(tokenService, "refreshMemberToken", body, "app-cs"));
        assertEquals(IamErrorCode.MEMBER_TOKEN_ISSUE_DENIED.code(), ex.getErrorCode());
        verify(loginTokenClient, never()).fetchMemberTokenContext(anyLong(), anyString());
    }

    @Test
    void refreshMemberTokenRejectsMixedEmployeeClaims() {
        TokenJWTPayload body = memberBody();
        body.setUserId(1L);

        BusinessException ex = assertThrows(BusinessException.class, () ->
            ReflectionTestUtils.invokeMethod(tokenService, "refreshMemberToken", body, "app-cs"));
        assertEquals(SystemErrorCode.PARAM_VAL_INVALID.code(), ex.getErrorCode());
        verify(memberClient, never()).requireActiveForToken(anyLong(), anyLong());
    }

    @Test
    void refreshMemberTokenCallsEligibilityBeforeReissue() {
        TokenJWTPayload body = memberBody();
        MemberVo member = new MemberVo();
        member.setId(9L);
        member.setOrganId(10001L);
        member.setMemberNo("M9");
        member.setStatus("NORMAL");
        when(memberClient.requireActiveForToken(10001L, 9L))
            .thenReturn(Result.success(member));
        when(loginTokenClient.fetchMemberTokenContext(10001L, "app-cs"))
            .thenReturn(Result.error(SystemErrorCode.SYSTEM_INTERNAL_ERROR));

        BusinessException ex = assertThrows(BusinessException.class, () ->
            ReflectionTestUtils.invokeMethod(tokenService, "refreshMemberToken", body, "app-cs"));
        verify(memberClient).requireActiveForToken(10001L, 9L);
        verify(loginTokenClient).fetchMemberTokenContext(10001L, "app-cs");
        assertEquals(SystemErrorCode.SYSTEM_INTERNAL_ERROR.code(), ex.getErrorCode());
    }

    @Test
    void refreshMemberTokenRejectsWhenAcdNotBoundToTokenScopes() {
        TokenJWTPayload body = memberBody();

        BusinessException ex = assertThrows(BusinessException.class, () ->
            ReflectionTestUtils.invokeMethod(tokenService, "refreshMemberToken", body, "app-other"));
        assertEquals(SystemErrorCode.PARAM_VAL_INVALID.code(), ex.getErrorCode());
        verify(memberClient, never()).requireActiveForToken(anyLong(), anyLong());
        verify(loginTokenClient, never()).fetchMemberTokenContext(anyLong(), anyString());
    }

    @Test
    void refreshMemberTokenRejectsWhenApplicationScopesMissing() {
        TokenJWTPayload body = memberBody();
        body.setApplicationScopes(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
            ReflectionTestUtils.invokeMethod(tokenService, "refreshMemberToken", body, "app-cs"));
        assertEquals(SystemErrorCode.PARAM_REQUIRED.code(), ex.getErrorCode());
        verify(memberClient, never()).requireActiveForToken(anyLong(), anyLong());
    }

    @Test
    void memberExchangeErrorCodeIsDistinctFromAnonymous() {
        assertEquals("iam.40044", IamErrorCode.MEMBER_EXCHANGE_NOT_ALLOWED.code());
        assertEquals("iam.40021", IamErrorCode.ANONYMOUS_REFRESH_NOT_ALLOWED.code());
    }

    private static TokenJWTPayload memberBody() {
        TokenJWTPayload body = new TokenJWTPayload();
        body.setSessionType(SessionType.MEMBER);
        body.setMemberId(9L);
        body.setOrganId(10001L);
        body.setOrganType(OrganType.TENANT);
        body.setClientId("client-1");
        body.setClientPublicKey("{\"kty\":\"EC\"}");
        body.setApplicationScopes(List.of(new ApplicationScope(1L, "app-cs", 2L)));
        body.setRefreshExpireAt(Instant.now().getEpochSecond() + 3600);
        return body;
    }
}
