package com.g2rain.iam.utils;

import com.g2rain.common.utils.Strings;

/**
 * OAuth {@code state} 参数解析工具。
 */
public final class AuthorizationState {

    private static final String ANONYMOUS_PREFIX = Constants.STATE_ANONYMOUS + "|";

    private AuthorizationState() {
    }

    /**
     * 是否为匿名授权请求：{@code state=anonymous} 或 {@code state=anonymous|...}。
     */
    public static boolean isAnonymous(String state) {
        if (Strings.isBlank(state)) {
            return false;
        }
        String trimmed = state.trim();
        return Constants.STATE_ANONYMOUS.equals(trimmed) || trimmed.startsWith(ANONYMOUS_PREFIX);
    }

    /**
     * 匿名授权回调时应回传的 state：{@code anonymous|csrf} 取 {@code csrf} 部分；纯 {@code anonymous} 返回 {@code null}。
     */
    public static String resolveCallbackState(String state) {
        if (Strings.isBlank(state)) {
            return null;
        }
        String trimmed = state.trim();
        if (Constants.STATE_ANONYMOUS.equals(trimmed)) {
            return null;
        }
        if (trimmed.startsWith(ANONYMOUS_PREFIX)) {
            String callbackState = trimmed.substring(ANONYMOUS_PREFIX.length());
            return Strings.isBlank(callbackState) ? null : callbackState;
        }
        return trimmed;
    }
}
