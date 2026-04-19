package com.g2rain.iam.enums;


import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import lombok.Getter;

/**
 * 授权码模式、客户端模式和刷新令牌模式的枚举类，表示不同的 Token 授权类型。
 * <p>
 * 本类定义了 OAuth 2.0 中常见的三种授权模式：授权码模式、客户端凭证模式和刷新令牌模式。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 根据授权类型字符串获取对应的枚举
 * TokenGrantType grantType = TokenGrantType.fromCode("authorization_code");
 * }</pre>
 * </p>
 *
 * @author alpha
 * @since 2025/10/12
 */
@Getter
public enum TokenGrantType {

    /**
     * 授权码模式，通常用于第三方应用的授权。
     */
    AUTHORIZATION_CODE("authorization_code"),

    /**
     * 刷新令牌模式，通常用于获取新的访问令牌。
     */
    REFRESH_TOKEN("refresh_token"),

    /**
     * 交换令牌模式(用于切换用户身份)
     */
    EXCHANGE_TOKEN("exchange_token");

    /**
     * 授权类型对应的字符串表示。
     */
    private final String code;

    /**
     * 构造方法，初始化授权类型对应的字符串。
     *
     * @param code 授权类型的字符串表示
     */
    TokenGrantType(String code) {
        this.code = code;
    }

    /**
     * 根据授权类型字符串获取对应的枚举。
     * <p>
     * 如果字符串匹配成功，则返回对应的枚举类型，否则抛出异常。
     * </p>
     *
     * @param code 授权类型的字符串
     * @return {@link TokenGrantType} 对应的枚举值
     * @throws BusinessException 如果没有匹配的授权类型，则抛出业务异常
     */
    public static TokenGrantType fromCode(String code) {
        for (TokenGrantType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }

        throw new BusinessException(SystemErrorCode.RESOURCE_NOT_FOUND, code);
    }
}
