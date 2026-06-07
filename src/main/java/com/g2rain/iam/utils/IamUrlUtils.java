package com.g2rain.iam.utils;

/**
 * IAM 绝对 URL 拼接工具（base + 多段 path）。
 */
public final class IamUrlUtils {

    private IamUrlUtils() {
    }

    /**
     * 去掉首尾空白与末尾 {@code /}。
     */
    public static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String base = url.trim();
        while (!base.isEmpty() && base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    /**
     * 非空段补前导 {@code /}；null 或空白返回空串。
     */
    public static String ensureLeadingSlash(String segment) {
        if (segment == null || segment.isBlank()) {
            return "";
        }
        String s = segment.trim();
        return s.startsWith("/") ? s : "/" + s;
    }

    /**
     * 拼接绝对 URL：{@code base} 为根（可含 scheme），{@code pathSegments} 为依次追加的路径段。
     * <p>空段跳过；{@code base} 与每段 path 均会 trim，path 自动补前导 {@code /}。</p>
     *
     * @param base          根 URL，如 {@code https://host}
     * @param pathSegments  路径段，如 {@code /main}、{@code /passport/bind_result}
     * @return 拼接结果
     */
    public static String joinAbsoluteUrl(String base, String... pathSegments) {
        String result = trimTrailingSlash(base);
        if (pathSegments == null) {
            return result;
        }
        for (String segment : pathSegments) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            result += ensureLeadingSlash(segment);
        }
        return result;
    }
}
