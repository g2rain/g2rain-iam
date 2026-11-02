package com.g2rain.iam.dingtalk;

/**
 * 钉钉 OAuth 回调换票结果
 *
 * @param sessionId        IAM 会话 ID
 * @param clientId         OAuth2 客户端 ID
 * @param redirectUri      OAuth2 回调地址
 * @param state            业务系统 state
 * @param applicationCode  开放平台目标应用编码
 * @param transactionId    授权事务 tid（可空，旧链路）
 */
public record DingTalkOAuthResult(
    String sessionId,
    String clientId,
    String redirectUri,
    String state,
    String applicationCode,
    String transactionId
) {
}
