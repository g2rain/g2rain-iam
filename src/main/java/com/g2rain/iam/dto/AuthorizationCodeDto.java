package com.g2rain.iam.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;


/**
 * 授权码 DTO 类，用于在授权码授权流程中传递会话 ID、客户端 ID 和用户 ID 等信息。
 * <p>
 * 存储与授权码相关的关键数据，供换票时校验 session、IdP 上下文及可选的目标应用绑定（{@code applicationCode}）。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * AuthorizationCodeDto dto = new AuthorizationCodeDto();
 * dto.setSessionId("session123");
 * dto.setClientId("client123");
 * dto.setUserId("user123");
 * }</pre>
 * </p>
 *
 * @author alpha
 * @since 2025/10/13
 */
@Setter
@Getter
@NoArgsConstructor
public class AuthorizationCodeDto {
    /**
     * 会话 ID，标识用户的当前会话。
     */
    private String sessionId;

    /**
     * 客户端 ID，标识请求授权的客户端。
     */
    private String clientId;

    /**
     * 用户 ID，标识授权码授权请求中的用户。
     */
    private String userId;

    /**
     * 为 true 表示授权码由外部身份源（如钉钉）会话发码；换 token 时 Basis 在存在业务用户 ID 时校验
     * {@code application_idp_provision} 与 {@code passport_idp_binding}（须携带 idp 上下文字段）。
     */
    private Boolean thirdPartyIdpLogin;

    /**
     * 发码时会话中的身份源类型，与 {@link com.g2rain.basis.enums.IdpType} 枚举名一致。
     */
    private String idpType;

    /**
     * 发码时会话中的 IdP 稳定主体（如钉钉 unionId）。
     */
    private String idpSubject;

    /**
     * 发码时会话中的三方应用标识（如钉钉 OAuth clientId）。
     */
    private String idpApplicationCode;

    /**
     * 发码时会话中的 IdP 接入形态，与 {@link com.g2rain.basis.enums.IdpBindMode} 及
     * {@code passport_idp_binding.bind_mode} 存库值一致。
     */
    private String idpBindMode;

    /**
     * 为 true 表示匿名授权发码；换票时不查 session，走 {@code fetchAnonymousTokenContext}。
     */
    private Boolean anonymous;

    /**
     * 匿名发码时的机构 ID；或目标应用确认发码时 Basis 返回的机构 ID。
     */
    private Long organId;

    /**
     * 匿名发码时 IAM 配置的角色 ID 列表。
     */
    private List<Long> roleIds;

    /**
     * 目标应用编码；有值时换票须与 Client DPoP {@code acd} 一致，且 Redis 中授权码 TTL 为 5 分钟。
     */
    private String applicationCode;

    /**
     * 目标应用 ID（用户确认并 {@code activate_self} 后写入）。
     */
    private Long applicationId;

    /**
     * 授权码签发时间（Unix 秒）。
     */
    private Long issuedAt;

    /**
     * 授权码过期时间（Unix 秒）。
     */
    private Long expiresAt;
}
