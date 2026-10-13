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

/**
 * 会员解析码（{@code memberResolveCode}）签发与校验。
 * <p>
 * 短时可复用：同一次客服回调可多次换票，不做单次消费删除。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class MemberResolveCodeService {

    private final GenericRedisHelper redis;
    private final WeComIamProperties weComIamProperties;

    /**
     * 签发短时 memberResolveCode 并写入 Redis。
     *
     * @param payload 解析码载荷（含 organId 等）
     * @return 明文码与过期时间
     */
    public IssuedCode issue(MemberResolveCodeDto payload) {
        String code = IamUtils.generateAuthorizationCode();
        long ttlSeconds = Math.max(60L,
            weComIamProperties.getCustomerService().getMemberResolveCodeTtlSeconds());
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
        redis.set(RedisKeyRule.MEMBER_RESOLVE_CODE.format(code), payload, Duration.ofSeconds(ttlSeconds));
        return new IssuedCode(code, expiresAt);
    }

    /**
     * 校验短时可复用票据：只读 Redis，不做单次消费（批消息可重复换票）。
     */
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

    /**
     * 已签发的解析码与过期时间。
     *
     * @param code      明文解析码
     * @param expiresAt 过期时刻
     */
    public record IssuedCode(String code, Instant expiresAt) {
    }
}
