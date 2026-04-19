package com.g2rain.iam.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 生成令牌请求 DTO 类，包含生成令牌所需的各种字段。
 * <p>
 * 该类用于接收生成令牌请求的相关参数，其中包括客户端认证信息、授权码、重定向 URI 等。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 创建一个新的生成令牌 DTO 并设置属性
 * GenerateTokenDto dto = new GenerateTokenDto();
 * dto.setGrantType("authorization_code");
 * dto.setApplicationId("app123");
 * dto.setClientId("client123");
 * dto.setClientPublicKey("publicKey123");
 * dto.setRedirectUri("https://example.com/callback");
 * dto.setCode("authCode123");
 * dto.setClientSecret("clientSecret123");
 * }</pre>
 * </p>
 *
 * @author alpha
 * @since 2025/10/11
 */
@Setter
@Getter
@NoArgsConstructor
public class GenerateTokenDto {

    /**
     * 授权类型，表示请求生成令牌的授权方式，如 `authorization_code`。
     * <p>
     * 该字段不能为空，且必须使用 `@NotBlank` 注解进行验证。
     * </p>
     */
    @NotBlank
    private String grantType;

    /**
     * 授权码，授权码模式下使用的授权凭证。
     * <p>
     * 该字段可选，只有在授权码模式下需要传入。
     * </p>
     */
    private String code;

    /**
     * 交换令牌模式
     * <p>
     * 该字段可选，只有在交换令牌模式下需要传入。
     * </p>
     */
    private Long userId;
}
