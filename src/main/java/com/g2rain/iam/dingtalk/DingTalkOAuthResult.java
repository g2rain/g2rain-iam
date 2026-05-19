package com.g2rain.iam.dingtalk;

/**
 * 钉钉 OAuth 回调换票结果
 * 功能：携带会话 ID 与后续 redirectConsent 所需的 OAuth 参数
 *
 * @author Alpha
 * @param sessionId   IAM 会话 ID
 * @param clientId    OAuth2 客户端 ID
 * @param redirectUri OAuth2 回调地址
 * @param state       业务系统 state
 */
public record DingTalkOAuthResult(String sessionId, String clientId, String redirectUri, String state) {
}
