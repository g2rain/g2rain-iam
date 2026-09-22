package com.g2rain.iam.service;

import com.g2rain.common.utils.Strings;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.dto.AuthorizationCodeDto;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.enums.RedisKeyRule;
import com.g2rain.iam.utils.IamUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;


/**
 * 授权服务，提供生成和管理授权码的功能。
 * <p>
 * 该服务用于 OAuth2 授权码模式下的授权码生成，将授权码与会话和客户端信息关联，并在 Redis 中存储临时数据。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * String code = authorizationService.generateAuthorizationCode(session, "client123", "user456");
 * }</pre>
 * </p>
 *
 * @author alpha
 * @since 2025/10/10
 */
@Service
public class AuthorizationService {

    /**
     * 通用 Redis 辅助类，用于存储授权码和会话信息。
     */
    @Resource
    private GenericRedisHelper genericRedisHelper;

    /**
     * 生成授权码并存储在 Redis 中（默认 TTL 10 分钟）。
     *
     * @param session              当前用户会话信息
     * @param clientId             客户端 ID
     * @param userId               用户 ID
     * @param thirdPartyIdpLogin   是否外部身份源（如钉钉）授权链路发码
     * @return 生成的授权码
     */
    public String generateAuthorizationCode(SessionDto session, String clientId, String userId, boolean thirdPartyIdpLogin) {
        return generateAuthorizationCode(session, clientId, userId, thirdPartyIdpLogin, null, null, null);
    }

    /**
     * 生成授权码并写入 Redis。
     * <p>
     * 绑定 {@code applicationCode} 时额外写入 {@code applicationId}、{@code organId}，TTL 为 5 分钟；否则 TTL 为 10 分钟。
     * </p>
     */
    public String generateAuthorizationCode(
        SessionDto session,
        String clientId,
        String userId,
        boolean thirdPartyIdpLogin,
        String applicationCode,
        Long applicationId,
        Long organId) {
        Duration ttl = Strings.isNotBlank(applicationCode) ? Duration.ofMinutes(5) : Duration.ofMinutes(10);
        return storeAuthorizationCode(
            session, clientId, userId, thirdPartyIdpLogin, applicationCode, applicationId, organId, ttl);
    }

    /**
     * 与 {@link #generateAuthorizationCode(SessionDto, String, String, boolean)} 等价，{@code thirdPartyIdpLogin=false}（密码登录等）。
     */
    public String generateAuthorizationCode(SessionDto session, String clientId, String userId) {
        return generateAuthorizationCode(session, clientId, userId, false);
    }

    /**
     * 生成匿名授权码并存储在 Redis 中（无 session/user）。
     *
     * @param clientId 客户端 ID（DPoP kid）
     * @param organId  机构 ID
     * @param roleIds  角色 ID 列表
     * @return 授权码
     */
    public String generateAnonymousAuthorizationCode(String clientId, Long organId, List<Long> roleIds) {
        AuthorizationCodeDto codeDto = new AuthorizationCodeDto();
        codeDto.setClientId(clientId);
        codeDto.setAnonymous(true);
        codeDto.setOrganId(organId);
        codeDto.setRoleIds(roleIds);

        String code = IamUtils.generateAuthorizationCode();
        genericRedisHelper.set(
            RedisKeyRule.AUTHORIZATION_CODE.format(code),
            codeDto,
            Duration.ofMinutes(10)
        );
        return code;
    }

    private String storeAuthorizationCode(
        SessionDto session,
        String clientId,
        String userId,
        boolean thirdPartyIdpLogin,
        String applicationCode,
        Long applicationId,
        Long organId,
        Duration ttl) {
        AuthorizationCodeDto codeDto = new AuthorizationCodeDto();
        codeDto.setSessionId(session.getSessionId());
        codeDto.setClientId(clientId);
        codeDto.setUserId(userId);
        codeDto.setThirdPartyIdpLogin(thirdPartyIdpLogin);
        codeDto.setIdpType(Strings.isBlank(session.getIdpType()) ? null : session.getIdpType().trim());
        codeDto.setIdpSubject(Strings.isBlank(session.getIdpSubject()) ? null : session.getIdpSubject().trim());
        codeDto.setIdpApplicationCode(Strings.isBlank(session.getIdpApplicationCode()) ? null : session.getIdpApplicationCode().trim());
        codeDto.setIdpBindMode(Strings.isBlank(session.getIdpBindMode()) ? null : session.getIdpBindMode().trim());

        Instant now = Instant.now();
        codeDto.setIssuedAt(now.getEpochSecond());
        codeDto.setExpiresAt(now.plus(ttl).getEpochSecond());

        if (Strings.isNotBlank(applicationCode)) {
            codeDto.setApplicationCode(applicationCode.trim());
            codeDto.setApplicationId(applicationId);
            codeDto.setOrganId(organId);
        }

        // 生成授权码并存入 Redis
        String code = IamUtils.generateAuthorizationCode();
        genericRedisHelper.set(
            RedisKeyRule.AUTHORIZATION_CODE.format(code),
            codeDto,
            ttl
        );
        return code;
    }
}
