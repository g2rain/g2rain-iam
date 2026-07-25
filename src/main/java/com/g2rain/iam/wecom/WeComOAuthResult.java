package com.g2rain.iam.wecom;

/**
 * 企业微信 OAuth 回调换票结果
 */
public record WeComOAuthResult(
    String sessionId,
    String clientId,
    String redirectUri,
    String state
) {
}
