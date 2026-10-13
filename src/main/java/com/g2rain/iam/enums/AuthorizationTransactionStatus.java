package com.g2rain.iam.enums;

/**
 * 授权事务状态机。终态：{@link #DENIED}、{@link #COMPLETED}、{@link #CANCELLED}、{@link #FAILED}。
 */
public enum AuthorizationTransactionStatus {
    CREATED,
    IDP_PENDING,
    AUTHENTICATED,
    USER_SELECTED,
    CONSENT_REQUIRED,
    ACTIVATING,
    DENIED,
    COMPLETED,
    CANCELLED,
    FAILED;

    /**
     * 是否为终态（不可再推进，仅可幂等回读结果）。
     *
     * @return 终态时返回 {@code true}
     */
    public boolean isTerminal() {
        return this == DENIED || this == COMPLETED || this == CANCELLED || this == FAILED;
    }
}
