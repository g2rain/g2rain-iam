package com.g2rain.iam.wecom;

/**
 * 验签解密成功后的最小化结果。
 */
public record VerifiedWeComCallback(
    String plainBody,
    String receiver
) {
}
