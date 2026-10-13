package com.g2rain.iam.enums;

/**
 * 浏览器授权事务内允许的登录方式。
 */
public enum LoginMethod {

    /**
     * 账号密码。
     */
    PASSWORD,

    /**
     * 钉钉扫码 / 跳转登录。
     */
    DINGTALK,

    /**
     * 企业微信扫码登录。
     */
    WECOM
}
