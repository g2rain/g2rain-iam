package com.g2rain.iam.enums;


import com.g2rain.common.exception.ErrorCode;

/**
 * @author alpha
 * @since 2025/12/31
 */
public enum IamErrorCode implements ErrorCode {

    REFRESH_TOKEN_EXPIRED("iam.40102", "刷新 Token 过期");

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
