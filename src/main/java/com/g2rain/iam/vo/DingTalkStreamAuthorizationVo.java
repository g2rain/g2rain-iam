package com.g2rain.iam.vo;

/**
 * 钉钉 Stream 场景 OAuth 授权码响应。
 *
 * @param code  授权码
 * @param state OAuth state（与请求一致，可为 null）
 */
public record DingTalkStreamAuthorizationVo(String code, String state) {
}
