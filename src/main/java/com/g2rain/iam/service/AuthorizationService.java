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
import java.util.List;


/**
 * 授权服务，提供生成和管理授权码的功能。
 * <p>
 * 该服务用于 OAuth2 授权码模式下的授权码生成，将授权码与会话和客户端信息关联，并在 Redis 中存储临时数据。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 根据当前会话和客户端信息生成授权码
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
     * 生成授权码并存储在 Redis 中。
     * <p>
     * 根据当前会话、客户端 ID 和用户 ID 生成唯一授权码，并将对应的 {@link AuthorizationCodeDto} 对象存入 Redis，
     * 设置过期时间为 10 分钟。
     * </p>
     *
     * @param session  当前用户会话信息
     * @param clientId 客户端 ID
     * @param userId               用户 ID
     * @param thirdPartyIdpLogin   是否外部身份源（如钉钉）授权链路发码
     * @return {@link String} 生成的授权码
     */
    public String generateAuthorizationCode(SessionDto session, String clientId, String userId, boolean thirdPartyIdpLogin) {
        // 1. 校验用户名 + 密码是否正确
        AuthorizationCodeDto codeDto = new AuthorizationCodeDto();
        codeDto.setSessionId(session.getSessionId());
        codeDto.setClientId(clientId);
        codeDto.setUserId(userId);
        codeDto.setThirdPartyIdpLogin(thirdPartyIdpLogin);
        codeDto.setIdpType(Strings.isBlank(session.getIdpType()) ? null : session.getIdpType().trim());
        codeDto.setIdpSubject(Strings.isBlank(session.getIdpSubject()) ? null : session.getIdpSubject().trim());
        codeDto.setIdpApplicationCode(Strings.isBlank(session.getIdpApplicationCode()) ? null : session.getIdpApplicationCode().trim());

        // 2. 生成授权码
        String code = IamUtils.generateAuthorizationCode();

        // 3. 存 session，10 分钟过期
        genericRedisHelper.set(
            RedisKeyRule.AUTHORIZATION_CODE.format(code),
            codeDto,
            Duration.ofMinutes(10)
        );

        return code;
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
}
