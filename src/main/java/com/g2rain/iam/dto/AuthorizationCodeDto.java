package com.g2rain.iam.dto;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 授权码 DTO 类，用于在授权码授权流程中传递会话 ID、客户端 ID 和用户 ID 信息。
 * <p>
 * 该类用于存储与授权码相关的关键数据，例如会话 ID、客户端 ID 和用户 ID，这些信息将用于验证授权码请求。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 创建一个新的授权码 DTO
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
}
