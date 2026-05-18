package com.g2rain.iam.service;


import com.g2rain.basis.dto.LoginDto;
import com.g2rain.basis.dto.PassportDto;
import com.g2rain.basis.dto.PassportIdpBindingDto;
import com.g2rain.basis.dto.PassportIdpBindingSelectDto;
import com.g2rain.basis.enums.IdpType;
import com.g2rain.basis.vo.PassportIdpBindingVo;
import com.g2rain.basis.vo.PassportVo;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.ExceptionConverter;
import com.g2rain.common.model.Result;
import com.g2rain.common.utils.Collections;
import com.g2rain.common.utils.Strings;
import com.g2rain.data.redis.GenericRedisHelper;
import com.g2rain.iam.client.LoginClient;
import com.g2rain.iam.client.PassportIdpBindingClient;
import com.g2rain.iam.dingtalk.DingTalkPrincipal;
import com.g2rain.iam.dto.SessionDto;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.RedisKeyRule;
import com.g2rain.iam.utils.Constants;
import com.g2rain.iam.utils.IamUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
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
@Slf4j
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
     * Basis 身份源绑定查询（钉钉登录后解析 passportId）。
     */
    @Resource
    private PassportIdpBindingClient passportIdpBindingClient;

    @Resource
    private PassportService passportService;

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

        // 4. 保存 session，24 小时过期
        genericRedisHelper.set(
            RedisKeyRule.SESSION.format(sessionId),
            session,
            Duration.ofHours(24)
        );

        return sessionId;
    }

    /**
     * 钉钉换票成功后建立 IAM 会话：与 {@link #authenticateDingTalk(DingTalkPrincipal, boolean)} 等价，
     * {@code autoProvisionMissingPassport=true}（浏览器 OAuth：无绑定时自动注册 passport 并写绑定）。
     */
    public String authenticateDingTalk(DingTalkPrincipal principal) {
        return authenticateDingTalk(principal, true);
    }

    /**
     * 钉钉换票成功后建立 IAM 会话：若 Basis 已存在 {@code passport_idp_binding} 则写入 {@link SessionDto#getPassportId()}。
     * <p>当 {@code autoProvisionMissingPassport} 为 {@code true} 且查询成功但无绑定时，在 Basis 注册 passport 并初始化绑定；
     * 为 {@code false} 时（Stream 发码）无绑定或查询失败则抛出 {@link IamErrorCode#DINGTALK_STREAM_USER_NOT_BOUND} 或 Basis 错误，不自动注册。</p>
     *
     * @param principal                    钉钉统一主体
     * @param autoProvisionMissingPassport {@code true} 允许无绑定时自动建号；{@code false} 必须已绑定
     * @return 新会话 ID
     */
    public String authenticateDingTalk(DingTalkPrincipal principal, boolean autoProvisionMissingPassport) {
        String passportId = null;
        String idpAppCode = principal.idpApplicationCode() == null ? "" : principal.idpApplicationCode().trim();
        PassportIdpBindingSelectDto query = new PassportIdpBindingSelectDto();
        query.setIdpType(IdpType.DINGTALK.name());
        query.setIdpSubject(principal.unionId());
        query.setIdpApplicationCode(idpAppCode);

        Result<List<PassportIdpBindingVo>> result = null;
        try {
            result = passportIdpBindingClient.selectList(query);
        } catch (Exception e) {
            log.warn("passport_idp_binding lookup failed: {}", e.toString());
        }

        boolean hasBinding = result != null && result.isSuccess() && Collections.isNotEmpty(result.getData());
        if (hasBinding) {
            passportId = Objects.toString(result.getData().getFirst().getPassportId(), null);
        } else if (!autoProvisionMissingPassport) {
            if (result != null && !result.isSuccess()) {
                throw ExceptionConverter.of(result);
            }
            throw new BusinessException(IamErrorCode.DINGTALK_STREAM_USER_NOT_BOUND);
        } else if (result != null && result.isSuccess()) {
            passportId = registerPassportAndDingTalkBinding(principal, idpAppCode, query);
        }

        String sessionId = IamUtils.generateSessionId();
        SessionDto session = new SessionDto();
        session.setSessionId(sessionId);
        session.setPassportId(passportId);
        session.setName(principal.nick());
        session.setIdpType(IdpType.DINGTALK.name());
        session.setIdpSubject(principal.unionId());
        session.setIdpBindMode(principal.bindMode());
        session.setIdpApplicationCode(principal.idpApplicationCode() == null ? "" : principal.idpApplicationCode().trim());

        genericRedisHelper.set(
            RedisKeyRule.SESSION.format(sessionId),
            session,
            Duration.ofHours(24)
        );
        return sessionId;
    }

    /**
     * Basis 新建 passport（钉钉专用登录名）并写入 {@code passport_idp_binding}；
     * 密码使用 {@link Constants#THIRD_PARTY_IDP_AUTO_REGISTER_PASSPORT_PASSWORD}（与其它第三方建号一致）。
     */
    private String registerPassportAndDingTalkBinding(
        DingTalkPrincipal principal,
        String idpApplicationCode,
        PassportIdpBindingSelectDto bindingQuery
    ) {
        String username = dingTalkPassportUsername(principal.unionId());
        PassportDto passportDto = new PassportDto();
        passportDto.setUsername(username);
        passportDto.setPassword(Constants.THIRD_PARTY_IDP_AUTO_REGISTER_PASSPORT_PASSWORD);
        String realName = Strings.isBlank(principal.nick()) ? "钉钉用户" : principal.nick().trim();
        if (realName.length() > 128) {
            realName = realName.substring(0, 128);
        }
        passportDto.setRealName(realName);
        passportDto.setPasswordTrusted(false);

        Result<?> passportSave = passportService.register(passportDto);
        if (!passportSave.isSuccess()) {
            Result<List<PassportIdpBindingVo>> again = passportIdpBindingClient.selectList(bindingQuery);
            if (again != null && again.isSuccess() && Collections.isNotEmpty(again.getData())) {
                return Objects.toString(again.getData().getFirst().getPassportId(), null);
            }
            throw ExceptionConverter.of(passportSave);
        }
        Long newPassportId = (Long) passportSave.getData();

        PassportIdpBindingDto bindingDto = new PassportIdpBindingDto();
        bindingDto.setPassportId(newPassportId);
        bindingDto.setIdpType(IdpType.DINGTALK.name());
        bindingDto.setIdpSubject(principal.unionId());
        bindingDto.setCorpId(Strings.isBlank(principal.corpId()) ? null : principal.corpId().trim());
        bindingDto.setIdpUserId(Strings.isBlank(principal.openId()) ? null : principal.openId().trim());
        bindingDto.setIdpApplicationCode(idpApplicationCode);
        bindingDto.setBindMode(principal.bindMode());
        String raw = principal.rawJson();
        bindingDto.setRawProfile(Strings.isBlank(raw) ? "{}" : raw);

        Result<Long> bindingSave = passportIdpBindingClient.save(bindingDto);
        if (!bindingSave.isSuccess()) {
            throw ExceptionConverter.of(bindingSave);
        }
        return Objects.toString(newPassportId, null);
    }

    /**
     * 与 {@code passport.username}（varchar 64）对齐的稳定登录名：{@code dt_} + unionId；过长时对 unionId 做摘要缩短。
     */
    private static String dingTalkPassportUsername(String unionId) {
        String prefix = "dt_";
        if (prefix.length() + unionId.length() <= 64) {
            return prefix + unionId;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(unionId.getBytes(StandardCharsets.UTF_8));
            return prefix + HexFormat.of().formatHex(digest, 0, 28);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
