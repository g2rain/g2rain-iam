package com.g2rain.iam.enums;

/**
 * IdP 员工扫码登录意图：USER=普通员工；ADMIN=企业管理员（租户开通）。
 * <p>禁止与 {@code SessionType.MEMBER}（客服会员）混淆。</p>
 */
public enum IdpLoginRole {
    USER,
    ADMIN;

    public static IdpLoginRole fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return USER;
        }
        return IdpLoginRole.valueOf(raw.trim().toUpperCase());
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }
}
