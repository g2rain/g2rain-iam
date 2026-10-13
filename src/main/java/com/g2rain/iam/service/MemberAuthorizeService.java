package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Strings;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.client.WechatWorkMemberClient;
import com.g2rain.iam.dto.MemberAuthorizeTokenRequest;
import com.g2rain.iam.dto.MemberResolveCodeDto;
import com.g2rain.iam.dto.MemberSessionTokenCacheDto;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.RedisKeyRule;
import com.g2rain.iam.vo.MemberAuthorizeTokenVo;
import com.g2rain.member.dto.WechatWorkMemberResolveRequest;
import com.g2rain.member.vo.WechatWorkMemberResolveVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 会员换票服务：校验 {@code memberResolveCode}、完成 DPoP 校验并签发 MEMBER Token。
 * <p>
 * Client/Application DPoP 须在任何 Member 写入前完成，避免无效证明仍创建会员。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberAuthorizeService {

    private static final String MEMBER_STATUS_NORMAL = "NORMAL";
    private static final DateTimeFormatter OFFSET_FORMATTER =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final MemberResolveCodeService memberResolveCodeService;
    private final WechatWorkMemberClient wechatWorkMemberClient;
    private final TokenService tokenService;
    private final GenericRedisHelper redis;

    /**
     * 校验 memberResolveCode 与 DPoP，resolveOrCreate 会员后签发或复用 MEMBER Token。
     *
     * @param clientDPoP      客户端级 DPoP 证明
     * @param applicationDPoP 应用级 DPoP 证明
     * @param request         换票请求
     * @return 会员访问令牌视图
     */
    public MemberAuthorizeTokenVo token(
        String clientDPoP, String applicationDPoP, MemberAuthorizeTokenRequest request) {
        MemberResolveCodeDto codePayload =
            memberResolveCodeService.requireValid(request.getMemberResolveCode());
        String externalUserId = request.getExternalUserId().trim();

        // Client/Application DPoP 须在任何 Member 写入前完成，避免无效证明仍创建会员
        TokenService.MemberClientProof clientProof =
            tokenService.parseAndValidateMemberClientProof(clientDPoP, applicationDPoP);

        WechatWorkMemberResolveRequest resolveRequest = new WechatWorkMemberResolveRequest();
        resolveRequest.setOrganId(codePayload.getOrganId());
        resolveRequest.setExternalUserId(externalUserId);
        resolveRequest.setExternalProfile(request.getExternalProfile());

        Result<WechatWorkMemberResolveVo> result =
            wechatWorkMemberClient.resolveOrCreate(resolveRequest);
        if (!result.isSuccess()) {
            throw ExceptionConverter.of(result);
        }
        WechatWorkMemberResolveVo member = result.getData();
        if (member == null || member.getMemberId() == null) {
            throw new BusinessException(IamErrorCode.MEMBER_TOKEN_ISSUE_DENIED);
        }
        if (!MEMBER_STATUS_NORMAL.equals(member.getMemberStatus())) {
            throw new BusinessException(IamErrorCode.MEMBER_TOKEN_ISSUE_DENIED);
        }

        MemberAuthorizeTokenVo reused = tryReuse(
            codePayload.getOrganId(), externalUserId, member, clientProof);
        if (reused != null) {
            return reused;
        }
        return issueNew(codePayload.getOrganId(), externalUserId, member, clientProof);
    }

    private MemberAuthorizeTokenVo tryReuse(
        Long organId,
        String externalUserId,
        WechatWorkMemberResolveVo member,
        TokenService.MemberClientProof clientProof) {
        String key = sessionCacheKey(organId, externalUserId, clientProof.applicationCode());
        MemberSessionTokenCacheDto cache = redis.get(key, MemberSessionTokenCacheDto.class);
        if (cache == null || Strings.isBlank(cache.getAccessToken()) || cache.getExpireAt() == null) {
            return null;
        }
        long now = Instant.now().getEpochSecond();
        if (cache.getExpireAt() <= now + 30) {
            return null;
        }
        if (!Objects.equals(clientProof.clientId(), cache.getClientId())
            || !Objects.equals(clientProof.clientPublicKey(), cache.getClientPublicKey())) {
            return null;
        }
        MemberAuthorizeTokenVo vo = new MemberAuthorizeTokenVo();
        vo.setAccessToken(cache.getAccessToken());
        vo.setTokenExpiresAt(formatEpoch(cache.getExpireAt()));
        vo.setMemberId(member.getMemberId());
        vo.setMemberNo(member.getMemberNo());
        vo.setMemberStatus(member.getMemberStatus());
        vo.setNewMember(member.getNewMember());
        vo.setIdentityVerified(member.getIdentityVerified());
        return vo;
    }

    private MemberAuthorizeTokenVo issueNew(
        Long organId,
        String externalUserId,
        WechatWorkMemberResolveVo member,
        TokenService.MemberClientProof clientProof) {
        TokenService.IssuedMemberToken tokenVo = tokenService.issueMemberAccessToken(
            clientProof,
            organId,
            member.getMemberId(),
            member.getMemberNo()
        );

        long expireAt = tokenVo.expireAt();
        long ttlSeconds = Math.max(60L, expireAt - Instant.now().getEpochSecond());

        MemberSessionTokenCacheDto cache = new MemberSessionTokenCacheDto();
        cache.setAccessToken(tokenVo.token());
        cache.setExpireAt(expireAt);
        cache.setMemberId(member.getMemberId());
        cache.setMemberNo(member.getMemberNo());
        cache.setMemberStatus(member.getMemberStatus());
        cache.setClientId(clientProof.clientId());
        cache.setClientPublicKey(clientProof.clientPublicKey());
        redis.set(
            sessionCacheKey(organId, externalUserId, clientProof.applicationCode()),
            cache,
            Duration.ofSeconds(ttlSeconds)
        );

        MemberAuthorizeTokenVo vo = new MemberAuthorizeTokenVo();
        vo.setAccessToken(tokenVo.token());
        vo.setTokenExpiresAt(formatEpoch(expireAt));
        vo.setMemberId(member.getMemberId());
        vo.setMemberNo(member.getMemberNo());
        vo.setMemberStatus(member.getMemberStatus());
        vo.setNewMember(member.getNewMember());
        vo.setIdentityVerified(member.getIdentityVerified());
        log.info("member authorize token issued organId={} memberId={} applicationCode={} externalUserIdLen={}",
            organId, member.getMemberId(), clientProof.applicationCode(), externalUserId.length());
        return vo;
    }

    private static String sessionCacheKey(Long organId, String externalUserId, String applicationCode) {
        return RedisKeyRule.MEMBER_SESSION_TOKEN.format(
            String.valueOf(organId), externalUserId, applicationCode);
    }

    private static String formatEpoch(long epochSeconds) {
        return OFFSET_FORMATTER.format(
            Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()));
    }
}
