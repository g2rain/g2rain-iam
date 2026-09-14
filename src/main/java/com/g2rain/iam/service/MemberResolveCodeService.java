package com.g2rain.iam.service;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.dto.MemberResolveCodeDto;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.RedisKeyRule;
import com.g2rain.iam.utils.IamUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MemberResolveCodeService {

    private final GenericRedisHelper redis;
    private final WeComIamProperties weComIamProperties;

    public IssuedCode issue(MemberResolveCodeDto payload) {
        String code = IamUtils.generateAuthorizationCode();
        long ttlSeconds = Math.max(60L,
            weComIamProperties.getCustomerService().getMemberResolveCodeTtlSeconds());
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
        redis.set(RedisKeyRule.MEMBER_RESOLVE_CODE.format(code), payload, Duration.ofSeconds(ttlSeconds));
        return new IssuedCode(code, expiresAt);
    }

    public MemberResolveCodeDto requireValid(String code) {
        if (Strings.isBlank(code)) {
            throw new BusinessException(IamErrorCode.MEMBER_RESOLVE_CODE_INVALID);
        }
        MemberResolveCodeDto payload = redis.get(
            RedisKeyRule.MEMBER_RESOLVE_CODE.format(code.trim()), MemberResolveCodeDto.class);
        if (payload == null || payload.getOrganId() == null) {
            throw new BusinessException(IamErrorCode.MEMBER_RESOLVE_CODE_INVALID);
        }
        return payload;
    }

    public record IssuedCode(String code, Instant expiresAt) {
    }
}
