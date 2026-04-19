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
}
