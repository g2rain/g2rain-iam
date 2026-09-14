package com.g2rain.iam.service;

import com.g2rain.common.enums.SessionType;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.model.Result;
import com.g2rain.common.web.TokenJWTPayload;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.client.WechatWorkMemberClient;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.dto.MemberAuthorizeTokenRequest;
import com.g2rain.iam.dto.MemberResolveCodeDto;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.vo.MemberAuthorizeTokenVo;
import com.g2rain.iam.vo.TokenVo;
import com.g2rain.member.vo.WechatWorkMemberResolveVo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberAuthorizeServiceTest {

    @Test
    void tokenRejectsInvalidResolveCode() {
        MemberResolveCodeService codeService = mock(MemberResolveCodeService.class);
        when(codeService.requireValid(anyString()))
            .thenThrow(new BusinessException(IamErrorCode.MEMBER_RESOLVE_CODE_INVALID));
        MemberAuthorizeService service = new MemberAuthorizeService(
            codeService,
            mock(WechatWorkMemberClient.class),
            mock(TokenService.class),
            mock(GenericRedisHelper.class),
            new WeComIamProperties()
        );
        MemberAuthorizeTokenRequest request = new MemberAuthorizeTokenRequest();
        request.setMemberResolveCode("bad");
        request.setExternalUserId("ext-1");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.token(request));
        assertEquals(IamErrorCode.MEMBER_RESOLVE_CODE_INVALID.code(), ex.getErrorCode());
    }

    @Test
    void tokenDoesNotIssueWhenMemberFrozen() {
        MemberResolveCodeService codeService = mock(MemberResolveCodeService.class);
        MemberResolveCodeDto payload = new MemberResolveCodeDto();
        payload.setOrganId(10001L);
        when(codeService.requireValid(anyString())).thenReturn(payload);

        WechatWorkMemberClient memberClient = mock(WechatWorkMemberClient.class);
        WechatWorkMemberResolveVo member = new WechatWorkMemberResolveVo();
        member.setMemberId(9L);
        member.setMemberStatus("FROZEN");
        when(memberClient.resolveOrCreate(any())).thenReturn(Result.success(member));

        TokenService tokenService = mock(TokenService.class);
        MemberAuthorizeService service = new MemberAuthorizeService(
            codeService, memberClient, tokenService,
            mock(GenericRedisHelper.class), new WeComIamProperties());

        MemberAuthorizeTokenRequest request = new MemberAuthorizeTokenRequest();
        request.setMemberResolveCode("code");
        request.setExternalUserId("ext-1");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.token(request));
        assertEquals(IamErrorCode.MEMBER_TOKEN_ISSUE_DENIED.code(), ex.getErrorCode());
        verify(tokenService, never()).issueMemberAccessToken(any());
    }

    @Test
    void tokenIssuesMemberAccessTokenForNormalMember() {
        MemberResolveCodeService codeService = mock(MemberResolveCodeService.class);
        MemberResolveCodeDto payload = new MemberResolveCodeDto();
        payload.setOrganId(10001L);
        when(codeService.requireValid(anyString())).thenReturn(payload);

        WechatWorkMemberClient memberClient = mock(WechatWorkMemberClient.class);
        WechatWorkMemberResolveVo member = new WechatWorkMemberResolveVo();
        member.setMemberId(9L);
        member.setMemberNo("M9");
        member.setMemberStatus("NORMAL");
        member.setNewMember(true);
        member.setIdentityVerified(true);
        when(memberClient.resolveOrCreate(any())).thenReturn(Result.success(member));

        TokenService tokenService = mock(TokenService.class);
        when(tokenService.issueMemberAccessToken(any())).thenReturn(new TokenVo("jwt", "kid"));
        GenericRedisHelper redis = mock(GenericRedisHelper.class);
        when(redis.get(anyString(), any())).thenReturn(null);

        WeComIamProperties properties = new WeComIamProperties();
        properties.getCustomerService().setMemberTokenTtlSeconds(1800);
        MemberAuthorizeService service = new MemberAuthorizeService(
            codeService, memberClient, tokenService, redis, properties);

        MemberAuthorizeTokenRequest request = new MemberAuthorizeTokenRequest();
        request.setMemberResolveCode("code");
        request.setExternalUserId("ext-1");

        MemberAuthorizeTokenVo vo = service.token(request);
        assertEquals("jwt", vo.getAccessToken());
        assertEquals(9L, vo.getMemberId());
        assertEquals("M9", vo.getMemberNo());

        ArgumentCaptor<TokenJWTPayload> payloadCaptor = ArgumentCaptor.forClass(TokenJWTPayload.class);
        verify(tokenService).issueMemberAccessToken(payloadCaptor.capture());
        TokenJWTPayload jwtPayload = payloadCaptor.getValue();
        assertEquals(SessionType.MEMBER, jwtPayload.getSessionType());
        assertEquals(10001L, jwtPayload.getOrganId());
        assertEquals(9L, jwtPayload.getMemberId());
        assertNull(jwtPayload.getUserId());
        assertNull(jwtPayload.getPassportId());
    }
}
