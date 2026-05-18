package com.g2rain.iam.utils;


/**
 * 常量类，包含与身份验证和重定向相关的常量。
 * <p>
 * 该类封装了与 OAuth2、SSO 或其他身份验证流程相关的常量。
 * </p>
 * <p>
 * 主要用于避免硬编码字符串，增强代码可读性和可维护性。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * String redirectUrl = Constants.REDIRECT + Constants.CLIENT_ID;
 * String sessionId = request.getSession().getId();
 * if (sessionId.equals(Constants.SESSION_NAME)) {
 *     // do something
 * }
 * }</pre>
 * </p>
 * <p>
 * 该类不可实例化，因为构造方法被私有化。
 *
 * @author alpha
 * @since 2025/10/11
 */
public class Constants {

    /**
     * 私有化构造方法，防止实例化。
     */
    private Constants() {
        // 防止实例化
    }

    /**
     * 重定向前缀，用于拼接 URL 的重定向部分。
     */
    public static final String REDIRECT = "redirect:";

    /**
     * 客户端 ID 字段名，通常用于标识发起请求的客户端。
     */
    public static final String CLIENT_ID = "clientId";

    /**
     * 重定向 URI 字段名，用于表示登录成功后的回调地址。
     */
    public static final String REDIRECT_URI = "redirectUri";

    /**
     * 状态字段名，通常用于防止 CSRF 攻击。
     */
    public static final String STATE = "state";

    /**
     * 授权码字段名，用于 OAuth2 等认证流程中传递授权码。
     */
    public static final String CODE = "code";

    public static final String DPoP_HEADER_TYPE = "dpop+jwt";

    /**
     * Session 名称字段，通常用于存储用户的身份验证会话 ID。
     */
    public static final String SESSION_NAME = "G2RAIN_AUTH_SESSION_ID";

    /**
     * 第三方身份源（钉钉等）自动建号时写入 Basis 的占位密码：新建 passport 必填字段，
     * 实际登录走 SSO；须配合 {@code password_trusted=false}，禁止用户名密码登录。
     */
    public static final String THIRD_PARTY_IDP_AUTO_REGISTER_PASSPORT_PASSWORD = "123456";

    public static final String REQUEST_ID = "requestId";
}
