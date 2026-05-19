package com.g2rain.iam.enums;


import com.g2rain.common.exception.ErrorCode;

/**
 * @author alpha
 * @since 2025/12/31
 */
public enum IamErrorCode implements ErrorCode {

    REFRESH_TOKEN_EXPIRED("iam.40102", "刷新 Token 过期"),

    DINGTALK_OAUTH_INVALID_STATE("iam.40011", "无效或已过期的 state"),

    DINGTALK_TOKEN_EXCHANGE_FAILED("iam.50210", "钉钉换票失败"),

    DINGTALK_USERINFO_FAILED("iam.50211", "钉钉用户信息获取失败"),

    DINGTALK_STREAM_USER_NOT_BOUND("iam.40012", "钉钉账号未绑定系统通行证"),

    DINGTALK_IDP_BINDING_LOOKUP_FAILED("iam.50212", "身份源绑定查询失败，请稍后重试"),

    DINGTALK_SESSION_PASSPORT_MISSING("iam.50213", "钉钉登录会话缺少通行证标识"),

    /**
     * 授权码换 token 时，DPoP kid 须与发码时写入的 OAuth 客户端 ID 一致。
     */
    OAUTH_AUTHORIZATION_CODE_CLIENT_MISMATCH("iam.40015", "授权码与当前客户端不匹配");

    private final String code;

    private final String messageTemplate;

    /**
     * 构造系统错误码
     *
     * @param code            错误码（遵循4xxx客户端错误，5xxx服务器错误）
     * @param messageTemplate 消息模板（支持{0:param}顺序占位符或{key}键值对占位符）
     */
    IamErrorCode(String code, String messageTemplate) {
        this.code = code;
        this.messageTemplate = messageTemplate;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String messageTemplate() {
        return messageTemplate;
    }
}
