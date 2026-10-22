package com.g2rain.iam.enums;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.exception.SystemErrorCode;
import com.g2rain.common.utils.Strings;

import java.util.Arrays;

/**
 * 授权事务模式：用户登录发码或匿名发码。
 */
public enum AuthorizationMode {
    USER, ANONYMOUS;

    /**
     * 解析授权请求的 {@code mode} 参数；未传时为 {@link #USER}。
     *
     * @param name 模式名称
     * @return 授权模式
     * @throws BusinessException 名称非法时抛出
     */
    public static AuthorizationMode of(String name) {
        if (Strings.isBlank(name)) {
            return USER;
        }

        String trimmed = name.trim();
        return Arrays.stream(values())
            .filter(mode -> mode.name().equals(trimmed))
            .findFirst()
            .orElseThrow(() -> new BusinessException(SystemErrorCode.PARAM_VAL_INVALID, "mode"));
    }
}
