package com.g2rain.iam.enums;

/**
 * 授权事务冻结的登录策略来源。
 */
public enum AuthPolicySource {

    /**
     * 命中 {@code g2rain.iam.auth-policy.applications} 专条。
     */
    APPLICATION,

    /**
     * 无 {@code applicationCode} 或 Map 未命中，使用 default。
     */
    PLATFORM_DEFAULT
}
