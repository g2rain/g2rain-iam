package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.model.Result;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.client.WechatWorkMemberClient;
import com.g2rain.iam.dto.MemberAuthorizeTokenRequest;
import com.g2rain.iam.dto.MemberResolveCodeDto;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.vo.MemberAuthorizeTokenVo;
import com.g2rain.member.vo.WechatWorkMemberResolveVo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberAuthorizeServiceTest {

    private static final TokenService.MemberClientProof CLIENT_PROOF =
        new TokenService.MemberClientProof("client-1", "app-cs", "{\"kty\":\"EC\"}");

    @Test
    void tokenRejectsInvalidResolveCode() {
        MemberResolveCodeService codeService = mock(MemberResolveCodeService.class);
        when(codeService.requireValid(anyString()))
            .thenThrow(new BusinessException(IamErrorCode.MEMBER_RESOLVE_CODE_INVALID));
        MemberAuthorizeService service = new MemberAuthorizeService(
            codeService,
            mock(WechatWorkMemberClient.class),
            mock(TokenService.class),
            mock(GenericRedisHelper.class)
        );
        MemberAuthorizeTokenRequest request = new MemberAuthorizeTokenRequest();
        request.setMemberResolveCode("bad");
        request.setExternalUserId("ext-1");

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.token("dpop", "app-dpop", request));
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
            codeService, memberClient, tokenService, mock(GenericRedisHelper.class));

        MemberAuthorizeTokenRequest request = new MemberAuthorizeTokenRequest();
        request.setMemberResolveCode("code");
        request.setExternalUserId("ext-1");

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.token("dpop", "app-dpop", request));
        assertEquals(IamErrorCode.MEMBER_TOKEN_ISSUE_DENIED.code(), ex.getErrorCode());
        verify(tokenService, never()).parseAndValidateMemberClientProof(anyString(), anyString());
        verify(tokenService, never()).issueMemberAccessToken(any(), anyLong(), anyLong(), any());
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
        when(tokenService.parseAndValidateMemberClientProof(anyString(), anyString()))
            .thenReturn(CLIENT_PROOF);
        when(tokenService.issueMemberAccessToken(eq(CLIENT_PROOF), eq(10001L), eq(9L), eq("M9")))
            .thenReturn(new TokenService.IssuedMemberToken("jwt", "kid", 1_700_000_000L));
        GenericRedisHelper redis = mock(GenericRedisHelper.class);
        when(redis.get(anyString(), any())).thenReturn(null);

        MemberAuthorizeService service = new MemberAuthorizeService(
            codeService, memberClient, tokenService, redis);

        MemberAuthorizeTokenRequest request = new MemberAuthorizeTokenRequest();
        request.setMemberResolveCode("code");
        request.setExternalUserId("ext-1");

        MemberAuthorizeTokenVo vo = service.token("dpop", "app-dpop", request);
        assertEquals("jwt", vo.getAccessToken());
        assertEquals(9L, vo.getMemberId());
        assertEquals("M9", vo.getMemberNo());

        verify(tokenService).parseAndValidateMemberClientProof("dpop", "app-dpop");
        verify(tokenService).issueMemberAccessToken(CLIENT_PROOF, 10001L, 9L, "M9");
    }
}
