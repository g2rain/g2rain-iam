package com.g2rain.iam.dingtalk;

/**
 * 钉钉浏览器 OAuth 回调完成后：会话 ID 与 OAuth 参数，供 Web 层写 Cookie 后走
 * {@link com.g2rain.iam.service.ModelAndViewService#redirectConsent}。
 */
public record DingTalkOAuthResult(String sessionId, String clientId, String redirectUri, String state) {
}
