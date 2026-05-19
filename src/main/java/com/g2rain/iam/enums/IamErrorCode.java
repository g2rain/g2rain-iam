package com.g2rain.iam.enums;


import com.g2rain.common.exception.ErrorCode;

/**
 * IAM 业务错误码枚举
 *
 * @author Alpha
 */
public enum IamErrorCode implements ErrorCode {

    /**
     * 刷新 Token 已过期
     */
    REFRESH_TOKEN_EXPIRED("iam.40102", "刷新 Token 过期"),

    /**
     * 钉钉 OAuth state 无效或已过期
     */
    DINGTALK_OAUTH_INVALID_STATE("iam.40011", "无效或已过期的 state"),

    /**
     * 钉钉授权码换票失败
     */
    DINGTALK_TOKEN_EXCHANGE_FAILED("iam.50210", "钉钉换票失败"),

    /**
     * 钉钉用户信息获取失败
     */
    DINGTALK_USERINFO_FAILED("iam.50211", "钉钉用户信息获取失败"),

    /**
     * Stream 发码时钉钉账号未绑定通行证
     */
    DINGTALK_STREAM_USER_NOT_BOUND("iam.40012", "钉钉账号未绑定系统通行证"),

    /**
     * 查询 passport_idp_binding 失败
     */
    DINGTALK_IDP_BINDING_LOOKUP_FAILED("iam.50212", "身份源绑定查询失败，请稍后重试"),

    /**
     * 钉钉登录会话缺少 passportId
     */
    DINGTALK_SESSION_PASSPORT_MISSING("iam.50213", "钉钉登录会话缺少通行证标识"),

    /**
     * 授权码与当前 OAuth 客户端不匹配
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
