package com.g2rain.iam.service;


import com.g2rain.basis.dto.LoginDto;
import com.g2rain.basis.vo.PassportVo;
import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.model.Result;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.client.LoginClient;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.enums.RedisKeyRule;
import com.g2rain.iam.utils.IamUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

/**
 * 认证服务，提供用户认证和会话管理功能。
 * <p>
 * 该服务用于处理用户登录、会话生成、会话查询以及会话有效性判断。
 * 会话信息会存储在 Redis 中，方便分布式环境下共享会话状态。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 用户登录认证
 * String sessionId = authService.authenticate("username", "password");
 *
 * // 检查会话是否过期
 * boolean expired = authService.isSessionExpired(sessionId);
 *
 * // 获取会话信息
 * SessionDto session = authService.getSession(sessionId);
 * }</pre>
 * </p>
 *
 * @author alpha
 * @since 2025/10/10
 */
@Service
public class AuthService {

    /**
     * 通用 Redis 辅助类，用于存储会话信息。
     */
    @Resource
    private GenericRedisHelper genericRedisHelper;

    /**
     * basis 服务登录的接口
     */
    @Resource
    private LoginClient loginClient;

    /**
     * 用户认证方法，根据用户名和密码生成会话。
     * <p>
     * 此方法会校验用户名和密码（此示例中为固定示例逻辑），生成唯一的会话 ID，
     * 并将会话信息存储在 Redis 中，设置有效期为 24 小时。
     * </p>
     *
     * @param username 用户名
     * @param password 密码
     * @return {@link String} 生成的会话 ID
     */
    public String authenticate(String username, String password) {
        // 1. 校验用户名+密码是否正确
        Result<PassportVo> result = loginClient.login(new LoginDto(username, password));
        if (!result.isSuccess()) {
            throw ExceptionConverter.of(result);
        }

        PassportVo passport = result.getData();

        // 2. 生成会话标识
        String sessionId = IamUtils.generateSessionId();

        // 3. 创建会话对象
        SessionDto session = new SessionDto();
        session.setSessionId(sessionId);
        session.setPassportId(Objects.toString(passport.getId(), null));
        session.setName(passport.getRealName());

        // 4. 保存 session, 30 分钟过期
        genericRedisHelper.set(
            RedisKeyRule.SESSION.format(sessionId),
            session,
            Duration.ofHours(24)
        );

        return sessionId;
    }

    /**
     * 判断会话是否过期。
     *
     * @param sessionId 会话 ID
     * @return {@code true} 如果会话不存在或已过期；{@code false} 如果会话有效
     */
    public boolean isSessionExpired(String sessionId) {
        return Objects.isNull(getSession(sessionId));
    }

    /**
     * 根据会话 ID 获取会话信息。
     *
     * @param sessionId 会话 ID
     * @return {@link SessionDto} 会话对象，如果会话不存在则返回 {@code null}
     */
    public SessionDto getSession(String sessionId) {
        return genericRedisHelper.get(
            RedisKeyRule.SESSION.format(sessionId),
            SessionDto.class
        );
    }

    /**
     * 登出方法，删除指定会话 ID 对应的 Redis 缓存。
     *
     * @param sessionId 会话 ID
     */
    public void logout(String sessionId) {
        if (Objects.nonNull(sessionId)) {
            genericRedisHelper.delete(RedisKeyRule.SESSION.format(sessionId));
        }
    }
}
