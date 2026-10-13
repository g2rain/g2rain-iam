package com.g2rain.iam.wecom;

/**
 * @param sessionId       IAM 会话 ID
 * @param clientId        OAuth2 客户端 ID
 * @param redirectUri     OAuth2 回调地址
 * @param state           业务 state
 * @param applicationCode 目标应用编码
 * @param transactionId   授权事务 tid
 */
public record WeComOAuthResult(
    String sessionId,
    String clientId,
    String redirectUri,
    String state,
    String applicationCode,
    String transactionId
) {
}
