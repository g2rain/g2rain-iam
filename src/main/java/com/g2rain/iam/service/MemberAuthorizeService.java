package com.g2rain.iam.service;

import com.g2rain.common.enums.SessionType;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Strings;
import com.g2rain.common.web.TokenJWTPayload;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.client.WechatWorkMemberClient;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.dto.MemberAuthorizeTokenRequest;
import com.g2rain.iam.dto.MemberResolveCodeDto;
import com.g2rain.iam.dto.MemberSessionTokenCacheDto;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.RedisKeyRule;
import com.g2rain.iam.vo.MemberAuthorizeTokenVo;
import com.g2rain.iam.vo.TokenVo;
import com.g2rain.member.dto.WechatWorkMemberResolveRequest;
import com.g2rain.member.vo.WechatWorkMemberResolveVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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
    private final WeComIamProperties weComIamProperties;

    public MemberAuthorizeTokenVo token(MemberAuthorizeTokenRequest request) {
        MemberResolveCodeDto codePayload =
            memberResolveCodeService.requireValid(request.getMemberResolveCode());
        String externalUserId = request.getExternalUserId().trim();

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

        MemberAuthorizeTokenVo reused = tryReuse(codePayload.getOrganId(), externalUserId, member);
        if (reused != null) {
            return reused;
        }
        return issueNew(codePayload.getOrganId(), externalUserId, member);
    }

    private MemberAuthorizeTokenVo tryReuse(
        Long organId, String externalUserId, WechatWorkMemberResolveVo member) {
        String key = RedisKeyRule.MEMBER_SESSION_TOKEN.format(
            String.valueOf(organId), externalUserId);
        MemberSessionTokenCacheDto cache = redis.get(key, MemberSessionTokenCacheDto.class);
        if (cache == null || Strings.isBlank(cache.getAccessToken()) || cache.getExpireAt() == null) {
            return null;
        }
        long now = Instant.now().getEpochSecond();
        if (cache.getExpireAt() <= now + 30) {
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
        Long organId, String externalUserId, WechatWorkMemberResolveVo member) {
        long ttlSeconds = Math.max(60L,
            weComIamProperties.getCustomerService().getMemberTokenTtlSeconds());
        long now = Instant.now().getEpochSecond();
        long expireAt = now + ttlSeconds;

        TokenJWTPayload payload = new TokenJWTPayload();
        payload.setSessionType(SessionType.MEMBER);
        payload.setOrganId(organId);
        payload.setMemberId(member.getMemberId());
        payload.setName(member.getMemberNo());
        payload.setIssuedAt(now);
        payload.setExpireAt(expireAt);
        payload.setRefreshExpireAt(expireAt);

        TokenVo tokenVo = tokenService.issueMemberAccessToken(payload);

        MemberSessionTokenCacheDto cache = new MemberSessionTokenCacheDto();
        cache.setAccessToken(tokenVo.getToken());
        cache.setExpireAt(expireAt);
        cache.setMemberId(member.getMemberId());
        cache.setMemberNo(member.getMemberNo());
        cache.setMemberStatus(member.getMemberStatus());
        redis.set(
            RedisKeyRule.MEMBER_SESSION_TOKEN.format(String.valueOf(organId), externalUserId),
            cache,
            Duration.ofSeconds(ttlSeconds)
        );

        MemberAuthorizeTokenVo vo = new MemberAuthorizeTokenVo();
        vo.setAccessToken(tokenVo.getToken());
        vo.setTokenExpiresAt(formatEpoch(expireAt));
        vo.setMemberId(member.getMemberId());
        vo.setMemberNo(member.getMemberNo());
        vo.setMemberStatus(member.getMemberStatus());
        vo.setNewMember(member.getNewMember());
        vo.setIdentityVerified(member.getIdentityVerified());
        log.info("member authorize token issued organId={} memberId={} externalUserIdLen={}",
            organId, member.getMemberId(), externalUserId.length());
        return vo;
    }

    private static String formatEpoch(long epochSeconds) {
        return OFFSET_FORMATTER.format(
            Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()));
    }
}
