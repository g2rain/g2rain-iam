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
     * 查询 passport_idp_binding 失败（身份源通用）
     */
    IDP_BINDING_LOOKUP_FAILED("iam.50212", "身份源绑定查询失败，请稍后重试"),

    /**
     * IdP 登录会话缺少 passportId（身份源通用）
     */
    IDP_SESSION_PASSPORT_MISSING("iam.50213", "身份源登录会话缺少通行证标识"),

    /**
     * 授权码与当前 OAuth 客户端不匹配
     */
    OAUTH_AUTHORIZATION_CODE_CLIENT_MISMATCH("iam.40015", "授权码与当前客户端不匹配"),

    /**
     * 通行证绑定钉钉 state 无效或已过期
     */
    DINGTALK_PASSPORT_BIND_INVALID_STATE("iam.40016", "绑定会话已失效，请重新发起绑定"),

    /**
     * 通行证绑定缺少登录凭证
     */
    DINGTALK_PASSPORT_BIND_UNAUTHORIZED("iam.40103", "请先登录后再绑定钉钉"),

    /**
     * 通行证绑定会话缺少 passportId 或 organId
     */
    DINGTALK_PASSPORT_BIND_CONTEXT_INVALID("iam.40017", "绑定会话上下文无效"),

    /**
     * Access Token 与当前客户端 DPoP 公钥不匹配
     */
    TOKEN_DPOP_KEY_MISMATCH("iam.40018", "Token 与客户端 DPoP 密钥不匹配"),

    /**
     * 通行证绑定钉钉失败（未分类或系统异常）
     */
    DINGTALK_PASSPORT_BIND_FAILED("iam.40019", "绑定失败，请稍后重试"),

    /**
     * 匿名 OAuth 授权未启用或配置不完整
     */
    ANONYMOUS_AUTH_DISABLED("iam.40020", "匿名授权未启用或配置不完整"),

    /**
     * 匿名 token 不允许 refresh
     */
    ANONYMOUS_REFRESH_NOT_ALLOWED("iam.40021", "匿名令牌不允许刷新"),

    /**
     * 钉钉通讯录 access token 获取失败
     */
    DINGTALK_CONTACT_ACCESS_TOKEN_FAILED("iam.50220", "钉钉通讯录 access token 获取失败"),

    /**
     * 钉钉通讯录拉取失败
     */
    DINGTALK_CONTACT_FETCH_FAILED("iam.50221", "钉钉通讯录拉取失败"),

    /**
     * 钉钉通讯录凭证未配置
     */
    DINGTALK_CONTACT_CREDENTIAL_MISSING("iam.50222", "钉钉通讯录凭证未配置"),

    /**
     * 钉钉通讯录接入形态暂不支持
     */
    DINGTALK_CONTACT_BIND_MODE_UNSUPPORTED("iam.40022", "钉钉通讯录同步暂不支持该接入形态"),

    /**
     * 钉钉成员缺少 unionId
     */
    DINGTALK_CONTACT_UNION_ID_MISSING("iam.50223", "钉钉成员缺少 unionId：{0:param}"),

    /**
     * 通讯录同步请求的 IdP 应用标识与 IAM 配置不一致
     */
    DINGTALK_CONTACT_APPLICATION_MISMATCH("iam.40023", "钉钉通讯录应用标识与 IAM 配置不一致"),

    /**
     * 通讯录同步请求的企业标识与 IAM 配置不一致
     */
    DINGTALK_CONTACT_CORP_MISMATCH("iam.40024", "钉钉通讯录企业标识与 IAM 配置不一致"),

    WECOM_OAUTH_INVALID_STATE("iam.40030", "企业微信登录 state 无效或已过期"),

    WECOM_CREDENTIAL_MISSING("iam.40031", "企业微信应用凭证未配置"),

    WECOM_TOKEN_EXCHANGE_FAILED("iam.50230", "企业微信换票失败"),

    WECOM_USERINFO_FAILED("iam.50231", "企业微信用户信息获取失败"),

    WECOM_ENTERPRISE_NOT_AUTHORIZED("iam.40032", "当前企业尚未安装或已取消企业微信第三方应用"),

    WECOM_AGENT_MISMATCH("iam.40033", "企业微信登录应用与企业安装授权不一致"),

    WECOM_CALLBACK_INVALID("iam.40034", "企业微信授权回调验签或解密失败"),

    /**
     * Stream / 消息应用发码时企业微信账号未绑定通行证
     */
    WECOM_STREAM_USER_NOT_BOUND("iam.40035", "企业微信账号未绑定系统通行证"),

    WECOM_AUTHORIZATION_EXCHANGE_FAILED("iam.50232", "企业微信安装授权换取永久授权码失败"),

    WECOM_CREDENTIAL_ENCRYPTION_FAILED("iam.50233", "企业微信永久授权凭证加密失败");

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
